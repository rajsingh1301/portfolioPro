package com.portfoliopro.trading;

import com.portfoliopro.auth.User;
import com.portfoliopro.auth.UserRepository;
import com.portfoliopro.market.StubFinnhub;
import com.portfoliopro.portfolio.CashTransactionRepository;
import com.portfoliopro.portfolio.CashTransactionType;
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

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@DisplayName("Trading API")
class TradingApiTest {

    private static StubFinnhub finnhub;

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RiskSettingsRepository riskSettingsRepository;
    @Autowired private HoldingRepository holdingRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private TradeRepository tradeRepository;
    @Autowired private CashTransactionRepository cashRepository;
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
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        // Foreign keys cascade from users, so this clears orders, trades, holdings and ledger too.
        riskSettingsRepository.deleteAll();
        userRepository.deleteAll();
        token = signUp("trader@example.com");
        userId = userRepository.findByEmail("trader@example.com").orElseThrow().getId();
        price("100.00");
    }

    @Test
    @DisplayName("a market buy fills, debits cash, and writes the order, trade, ledger row and holding together")
    void buyFills() throws Exception {
        order("AAPL", "BUY", 10).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FILLED"))
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.quantity").value(10));

        assertThat(cash()).isEqualByComparingTo("99000");
        Holding holding = holdingRepository.findByUserIdAndSymbol(userId, "AAPL").orElseThrow();
        assertThat(holding.getQuantity()).isEqualTo(10);
        assertThat(holding.getAvgPrice()).isEqualByComparingTo("100");
        assertThat(tradeRepository.findByUserIdOrderByIdDesc(userId)).hasSize(1);
        // Signup wrote the opening deposit; the buy is the one row after it.
        var ledger = cashRepository.findAll().stream().filter(c -> c.getType() != CashTransactionType.DEPOSIT).toList();
        assertThat(ledger).hasSize(1);
        assertThat(ledger.get(0).getAmount()).isEqualByComparingTo("-1000");
        assertThat(ledger.get(0).getBalanceAfter()).isEqualByComparingTo("99000");
    }

    @Test
    @DisplayName("a second buy at a different price averages the cost across all shares")
    void averageCost() throws Exception {
        order("AAPL", "BUY", 10).andExpect(status().isCreated());
        price("110.00");
        order("AAPL", "BUY", 10).andExpect(status().isCreated());

        Holding holding = holdingRepository.findByUserIdAndSymbol(userId, "AAPL").orElseThrow();
        assertThat(holding.getQuantity()).isEqualTo(20);
        assertThat(holding.getAvgPrice()).isEqualByComparingTo("105");
    }

    @Test
    @DisplayName("a sell credits cash and books realized P&L as (sell - avg) x quantity")
    void sellBooksPnl() throws Exception {
        order("AAPL", "BUY", 10).andExpect(status().isCreated());
        price("120.00");
        order("AAPL", "SELL", 4).andExpect(status().isCreated());

        assertThat(cash()).isEqualByComparingTo("99480"); // 99000 + 4 x 120
        Holding holding = holdingRepository.findByUserIdAndSymbol(userId, "AAPL").orElseThrow();
        assertThat(holding.getQuantity()).isEqualTo(6);
        assertThat(holding.getRealizedPnl()).isEqualByComparingTo("80"); // (120 - 100) x 4
        assertThat(holding.getAvgPrice()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("selling the whole position keeps the row so its realized P&L survives")
    void fullSellKeepsRow() throws Exception {
        order("AAPL", "BUY", 5).andExpect(status().isCreated());
        price("90.00");
        order("AAPL", "SELL", 5).andExpect(status().isCreated());

        Holding holding = holdingRepository.findByUserIdAndSymbol(userId, "AAPL").orElseThrow();
        assertThat(holding.getQuantity()).isZero();
        assertThat(holding.getRealizedPnl()).isEqualByComparingTo("-50");
    }

    @Test
    @DisplayName("a rejected order returns 422 and is still stored as REJECTED, with nothing else changed")
    void rejectionIsPersisted() throws Exception {
        order("AAPL", "SELL", 1).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ORDER_REJECTED"))
                .andExpect(jsonPath("$.message").value("Order rejected: not enough shares"));

        List<Order> orders = orderRepository.findByUserIdOrderByIdDesc(userId);
        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(orders.get(0).getRejectReason()).isEqualTo("not enough shares");
        assertThat(tradeRepository.count()).isZero();
        assertThat(cashRepository.findAll()).allMatch(c -> c.getType() == CashTransactionType.DEPOSIT);
        assertThat(holdingRepository.count()).isZero();
        assertThat(cash()).isEqualByComparingTo("100000");
    }

    @Test
    @DisplayName("insufficient balance, order too large and position limit each reject with their reason")
    void riskRejections() throws Exception {
        setCash("500");
        order("AAPL", "BUY", 6).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Order rejected: insufficient balance"));

        setCash("100000");
        order("AAPL", "BUY", 51).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Order rejected: order too large"));

        RiskSettings settings = riskSettingsRepository.findByUserId(userId).orElseThrow();
        settings.setMaxOrderValue(new BigDecimal("1000000"));
        riskSettingsRepository.save(settings);
        order("AAPL", "BUY", 201).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Order rejected: position limit"));
        order("AAPL", "BUY", 200).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("orders and trades are listed newest first and only for the caller")
    void listsAreScopedToTheUser() throws Exception {
        order("AAPL", "BUY", 1).andExpect(status().isCreated());
        order("AAPL", "BUY", 2).andExpect(status().isCreated());

        String other = signUp("other@example.com");
        mockMvc.perform(get("/api/orders").header("Authorization", "Bearer " + other))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/trades").header("Authorization", "Bearer " + other))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].quantity").value(2));
        mockMvc.perform(get("/api/trades").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].price").value("100.0000"))
                .andExpect(jsonPath("$[0].quantity").value(2));
    }

    @Test
    @DisplayName("bad input is 400, no token is 401, an unknown symbol is 404, an upstream failure is 503 and stores no order")
    void errors() throws Exception {
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"type\":\"MARKET\",\"quantity\":1}"))
                .andExpect(status().isUnauthorized());

        order("AAPL", "BUY", 0).andExpect(status().isBadRequest());
        order("AAPL", "HOLD", 1).andExpect(status().isBadRequest());
        orderOfType("AAPL", "BUY", "\"LIMIT\"", 1).andExpect(status().isBadRequest());

        finnhub.respondWith(200, "{\"c\":0,\"d\":0,\"dp\":0,\"h\":0,\"l\":0,\"o\":0,\"pc\":0,\"t\":0}");
        order("NOPE", "BUY", 1).andExpect(status().isNotFound());

        finnhub.respondWith(500, "{}");
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        order("AAPL", "BUY", 1).andExpect(status().isServiceUnavailable());

        assertThat(orderRepository.count()).isZero();
    }

    @Test
    @DisplayName("concurrent orders cannot spend the same cash: exactly one of eight $600 buys fits in $1,000")
    void concurrentBuysDoNotDoubleSpend() throws Exception {
        setCash("1000");
        // With $1,000 in total the default 20% cap would refuse a $600 buy on its own;
        // lift it so that only the cash check can reject, which is what the race is about.
        RiskSettings settings = riskSettingsRepository.findByUserId(userId).orElseThrow();
        settings.setMaxPositionPct(new BigDecimal("100.00"));
        riskSettingsRepository.save(settings);
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<Integer> call = () -> order("AAPL", "BUY", 6).andReturn().getResponse().getStatus();
                results.add(pool.submit(call));
            }
            int created = 0;
            int rejected = 0;
            for (Future<Integer> result : results) {
                int code = result.get();
                if (code == 201) created++;
                else if (code == 422) rejected++;
            }
            assertThat(created).isEqualTo(1);
            assertThat(rejected).isEqualTo(threads - 1);
        } finally {
            pool.shutdownNow();
        }

        assertThat(cash()).isEqualByComparingTo("400");
        assertThat(holdingRepository.findByUserIdAndSymbol(userId, "AAPL").orElseThrow().getQuantity()).isEqualTo(6);
        assertThat(tradeRepository.count()).isEqualTo(1);
        // Every attempt is on the record, including the ones that lost the race.
        assertThat(orderRepository.count()).isEqualTo(threads);
        assertThat(orderRepository.findAll().stream().filter(o -> o.getStatus() == OrderStatus.REJECTED)).hasSize(threads - 1);
    }

    // ---- helpers ----

    private org.springframework.test.web.servlet.ResultActions order(String symbol, String side, long quantity) throws Exception {
        return orderOfType(symbol, side, "\"MARKET\"", quantity);
    }

    private org.springframework.test.web.servlet.ResultActions orderOfType(String symbol, String side, String type, long quantity)
            throws Exception {
        String body = "{\"symbol\":\"%s\",\"side\":\"%s\",\"type\":%s,\"quantity\":%d}".formatted(symbol, side, type, quantity);
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/orders")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private void price(String value) {
        finnhub.respondWith(200, "{\"c\":%s,\"d\":0,\"dp\":0,\"h\":%s,\"l\":%s,\"o\":%s,\"pc\":%s,\"t\":1727539200}"
                .formatted(value, value, value, value, value));
        // The quote cache would otherwise keep serving the previous price.
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
        String response = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"Password123\"}".formatted(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return response.replaceAll("(?s).*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }
}
