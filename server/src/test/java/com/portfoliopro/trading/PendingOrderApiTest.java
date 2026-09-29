package com.portfoliopro.trading;

import com.portfoliopro.auth.User;
import com.portfoliopro.auth.UserRepository;
import com.portfoliopro.market.StubFinnhub;
import com.portfoliopro.portfolio.Holding;
import com.portfoliopro.portfolio.HoldingRepository;
import com.portfoliopro.risk.RiskSettings;
import com.portfoliopro.risk.RiskSettingsRepository;
import com.portfoliopro.support.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@DisplayName("Pending orders")
class PendingOrderApiTest {

    private static StubFinnhub finnhub;

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RiskSettingsRepository riskSettingsRepository;
    @Autowired private HoldingRepository holdingRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private TradeRepository tradeRepository;
    @Autowired private PendingOrderScheduler scheduler;
    @Autowired private TradingService tradingService;
    @Autowired private CacheManager cacheManager;

    private String token;
    private Long userId;

    @BeforeAll
    static void startStub() throws IOException {
        finnhub = StubFinnhub.start();
    }

    @AfterAll
    static void stopStub() {
        finnhub.stop();
    }

    @DynamicPropertySource
    static void finnhubProperties(DynamicPropertyRegistry registry) {
        registry.add("app.finnhub.base-url", () -> finnhub.baseUrl());
    }

    @BeforeEach
    void reset() throws Exception {
        finnhub.reset();
        clearCaches();
        riskSettingsRepository.deleteAll();
        userRepository.deleteAll();
        token = signUp("pending@example.com");
        userId = userRepository.findByEmail("pending@example.com").orElseThrow().getId();
        // Room for the position cap and order size not to be what these tests are about.
        RiskSettings settings = riskSettingsRepository.findByUserId(userId).orElseThrow();
        settings.setMaxPositionPct(new BigDecimal("100.00"));
        settings.setMaxOrderValue(new BigDecimal("1000000"));
        riskSettingsRepository.save(settings);
        price("100.00");
    }

    @Test
    @DisplayName("a limit buy is saved PENDING: no cash moves and no trade exists until it fills")
    void limitBuyIsPending() throws Exception {
        limitBuy(10, "95.00").andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.type").value("LIMIT"))
                .andExpect(jsonPath("$.limitPrice").value("95.0000"))
                .andExpect(jsonPath("$.triggerPrice").doesNotExist());

        assertThat(cash()).isEqualByComparingTo("100000");
        assertThat(tradeRepository.count()).isZero();
        assertThat(holdingRepository.count()).isZero();
    }

    @Test
    @DisplayName("a buy limit fills at the market price once it is at or below the limit, not before")
    void limitBuyFills() throws Exception {
        limitBuy(10, "95.00").andExpect(status().isCreated());

        scheduler.runOnce(); // price 100 > limit 95
        assertThat(only().getStatus()).isEqualTo(OrderStatus.PENDING);

        price("90.00");
        scheduler.runOnce();
        assertThat(only().getStatus()).isEqualTo(OrderStatus.FILLED);
        // Filled at the current price, which is better than the limit, not at the limit.
        assertThat(tradeRepository.findByUserIdOrderByIdDesc(userId)).singleElement()
                .satisfies(trade -> assertThat(trade.getPrice()).isEqualByComparingTo("90"));
        assertThat(cash()).isEqualByComparingTo("99100");
        assertThat(holding().getQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("a sell limit waits for the price to reach it, then fills and books realized P&L")
    void limitSellFills() throws Exception {
        market("BUY", 10);
        place("SELL", "LIMIT", 10, "110.00", null, null).andExpect(status().isCreated());

        price("105.00");
        scheduler.runOnce();
        assertThat(lastOrder().getStatus()).isEqualTo(OrderStatus.PENDING);

        price("112.00");
        scheduler.runOnce();
        assertThat(lastOrder().getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(holding().getQuantity()).isZero();
        assertThat(holding().getRealizedPnl()).isEqualByComparingTo("120"); // (112 - 100) x 10
    }

    @Test
    @DisplayName("a stop-loss sells once the price falls to its trigger, and at the price it has fallen to")
    void stopLossFills() throws Exception {
        market("BUY", 10);
        place("SELL", "STOP_LOSS", 10, null, "95.00", null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.triggerPrice").value("95.0000"));

        price("97.00");
        scheduler.runOnce();
        assertThat(lastOrder().getStatus()).isEqualTo(OrderStatus.PENDING);

        price("92.00"); // gapped below the trigger
        scheduler.runOnce();
        assertThat(lastOrder().getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(holding().getRealizedPnl()).isEqualByComparingTo("-80"); // (92 - 100) x 10
    }

    @Test
    @DisplayName("a buy with attachStopLoss creates a stop-loss below the fill price for the same shares")
    void attachedStopLoss() throws Exception {
        place("BUY", "MARKET", 10, null, null, true).andExpect(status().isCreated())
                .andExpect(jsonPath("$.attachStopLoss").value(true));

        Order stop = lastOrder();
        assertThat(stop.getType()).isEqualTo(OrderType.STOP_LOSS);
        assertThat(stop.getSide().name()).isEqualTo("SELL");
        assertThat(stop.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(stop.getQuantity()).isEqualTo(10);
        assertThat(stop.getTriggerPrice()).isEqualByComparingTo("95"); // default 5% below 100

        price("94.00");
        scheduler.runOnce();
        assertThat(lastOrder().getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(holding().getQuantity()).isZero();
    }

    @Test
    @DisplayName("shares promised to one pending sell cannot be promised again")
    void sharesAreNotDoubleBooked() throws Exception {
        market("BUY", 10);
        place("SELL", "STOP_LOSS", 10, null, "90.00", null).andExpect(status().isCreated());

        place("SELL", "LIMIT", 1, "120.00", null, null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Order rejected: not enough shares"));
        // Nor can a market sell take shares a pending stop-loss is waiting to sell.
        market("SELL", 1).andExpect(status().isUnprocessableEntity());
        // The refusals are on the record.
        assertThat(orderRepository.findAll().stream().filter(o -> o.getStatus() == OrderStatus.REJECTED)).hasSize(2);
    }

    @Test
    @DisplayName("risk is checked again at fill time: cash spent since placing makes the fill a rejection")
    void riskRechecked() throws Exception {
        limitBuy(10, "95.00").andExpect(status().isCreated());
        setCash("50");

        price("90.00");
        scheduler.runOnce();

        Order order = only();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.getRejectReason()).isEqualTo("insufficient balance");
        assertThat(tradeRepository.count()).isZero();
        assertThat(cash()).isEqualByComparingTo("50");
    }

    @Test
    @DisplayName("a cancelled order is never filled")
    void cancel() throws Exception {
        limitBuy(10, "95.00").andExpect(status().isCreated());
        Long id = only().getId();

        mockMvc.perform(delete("/api/orders/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        price("80.00");
        scheduler.runOnce();
        assertThat(only().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(tradeRepository.count()).isZero();
    }

    @Test
    @DisplayName("an order cancelled after the scheduler listed it is not filled when the scheduler gets to it")
    void cancelledAfterListed() throws Exception {
        limitBuy(10, "95.00").andExpect(status().isCreated());
        Long id = only().getId();
        mockMvc.perform(delete("/api/orders/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // What the scheduler does with an order it read as PENDING a moment earlier.
        tradingService.fillPending(userId, id, new BigDecimal("90.00"));

        assertThat(only().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(tradeRepository.count()).isZero();
        assertThat(cash()).isEqualByComparingTo("100000");
    }

    @Test
    @DisplayName("cancelling a settled order is 422, another user's order or an unknown one is 404")
    void cancelErrors() throws Exception {
        market("BUY", 1);
        Long filled = only().getId();
        mockMvc.perform(delete("/api/orders/" + filled).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_PENDING"));

        limitBuy(1, "50.00").andExpect(status().isCreated());
        Long pending = lastOrder().getId();
        String other = signUp("other@example.com");
        mockMvc.perform(delete("/api/orders/" + pending).header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/orders/999999").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/orders/" + pending)).andExpect(status().isUnauthorized());
        assertThat(orderRepository.findById(pending).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("price fields that do not fit the order type are 400")
    void validation() throws Exception {
        place("BUY", "LIMIT", 1, null, null, null).andExpect(status().isBadRequest());
        place("BUY", "LIMIT", 1, "10.00", "9.00", null).andExpect(status().isBadRequest());
        place("BUY", "LIMIT", 1, "-5.00", null, null).andExpect(status().isBadRequest());
        place("BUY", "LIMIT", 1, "1.23456", null, null).andExpect(status().isBadRequest());
        place("BUY", "STOP_LOSS", 1, null, "90.00", null).andExpect(status().isBadRequest());
        place("SELL", "STOP_LOSS", 1, null, null, null).andExpect(status().isBadRequest());
        place("SELL", "MARKET", 1, "100.00", null, null).andExpect(status().isBadRequest());
        place("SELL", "MARKET", 1, null, null, true).andExpect(status().isBadRequest());
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    @DisplayName("an unpriceable symbol leaves its orders pending and does not stop the others")
    void unavailablePriceIsSkipped() throws Exception {
        limitBuy(1, "95.00").andExpect(status().isCreated());
        place("BUY", "LIMIT", 1, "95.00", null, null, "MSFT").andExpect(status().isCreated());

        finnhub.respondForSymbol("MSFT", "{\"c\":90,\"d\":0,\"dp\":0,\"h\":90,\"l\":90,\"o\":90,\"pc\":90,\"t\":1727539200}");
        finnhub.respondWith(500, "{}");
        clearCaches();
        scheduler.runOnce(); // both symbols fail upstream: nothing throws, nothing fills

        assertThat(orderRepository.findAll()).allMatch(o -> o.getStatus() == OrderStatus.PENDING);
        assertThat(tradeRepository.count()).isZero();
    }

    @Test
    @DisplayName("a fill racing a cancel of the same order ends in exactly one outcome, never both")
    void fillRacesCancel() throws Exception {
        price("90.00"); // already below every limit, so the scheduler wants to fill
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 12; round++) {
                limitBuy(1, "95.00").andExpect(status().isCreated());
                Long id = lastOrder().getId();
                BigDecimal cashBefore = cash();
                long tradesBefore = tradeRepository.count();

                CountDownLatch go = new CountDownLatch(1);
                Future<Integer> cancelled = pool.submit(() -> {
                    go.await();
                    return mockMvc.perform(delete("/api/orders/" + id).header("Authorization", "Bearer " + token))
                            .andReturn().getResponse().getStatus();
                });
                Future<?> filled = pool.submit(() -> {
                    go.await();
                    scheduler.runOnce();
                    return null;
                });
                go.countDown();
                int cancelStatus = cancelled.get();
                filled.get();

                Order settled = orderRepository.findById(id).orElseThrow();
                if (settled.getStatus() == OrderStatus.CANCELLED) {
                    assertThat(cancelStatus).isEqualTo(200);
                    assertThat(tradeRepository.count()).isEqualTo(tradesBefore);
                    assertThat(cash()).isEqualByComparingTo(cashBefore);
                } else {
                    assertThat(settled.getStatus()).isEqualTo(OrderStatus.FILLED);
                    assertThat(cancelStatus).isEqualTo(422); // the cancel lost the race and was told so
                    assertThat(tradeRepository.count()).isEqualTo(tradesBefore + 1);
                    assertThat(cash()).isEqualByComparingTo(cashBefore.subtract(new BigDecimal("90")));
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // ---- helpers ----

    private ResultActions limitBuy(long quantity, String limit) throws Exception {
        return place("BUY", "LIMIT", quantity, limit, null, null);
    }

    private ResultActions market(String side, long quantity) throws Exception {
        return place(side, "MARKET", quantity, null, null, null);
    }

    private ResultActions place(String side, String type, long quantity, String limit, String trigger, Boolean attach)
            throws Exception {
        return place(side, type, quantity, limit, trigger, attach, "AAPL");
    }

    private ResultActions place(
            String side, String type, long quantity, String limit, String trigger, Boolean attach, String symbol)
            throws Exception {
        StringBuilder body = new StringBuilder("{\"symbol\":\"%s\",\"side\":\"%s\",\"type\":\"%s\",\"quantity\":%d"
                .formatted(symbol, side, type, quantity));
        if (limit != null) body.append(",\"limitPrice\":\"").append(limit).append('"');
        if (trigger != null) body.append(",\"triggerPrice\":\"").append(trigger).append('"');
        if (attach != null) body.append(",\"attachStopLoss\":").append(attach);
        body.append('}');
        return mockMvc.perform(MockMvcRequestBuilders.post("/api/orders")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.toString()));
    }

    /** The single order that exists. */
    private Order only() {
        List<Order> orders = orderRepository.findByUserIdOrderByIdDesc(userId);
        assertThat(orders).hasSize(1);
        return orders.get(0);
    }

    private Order lastOrder() {
        return orderRepository.findByUserIdOrderByIdDesc(userId).get(0);
    }

    private Holding holding() {
        return holdingRepository.findByUserIdAndSymbol(userId, "AAPL").orElseThrow();
    }

    private void price(String value) {
        finnhub.respondWith(200, "{\"c\":%s,\"d\":0,\"dp\":0,\"h\":%s,\"l\":%s,\"o\":%s,\"pc\":%s,\"t\":1727539200}"
                .formatted(value, value, value, value, value));
        clearCaches();
    }

    private void clearCaches() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    private BigDecimal cash() {
        return userRepository.findById(userId).orElseThrow().getCashBalance();
    }

    private void setCash(String amount) {
        User user = userRepository.findById(userId).orElseThrow();
        user.setCashBalance(new BigDecimal(amount));
        userRepository.save(user);
    }

    private String signUp(String email) throws Exception {
        String response = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"Password123\"}".formatted(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return response.replaceAll("(?s).*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }
}
