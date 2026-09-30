package com.portfoliopro.risk;

import com.portfoliopro.auth.UserRepository;
import com.portfoliopro.market.StubFinnhub;
import com.portfoliopro.support.IntegrationTest;
import com.portfoliopro.trading.Order;
import com.portfoliopro.trading.OrderRepository;
import com.portfoliopro.trading.OrderStatus;
import com.portfoliopro.trading.OrderType;
import com.portfoliopro.trading.PendingOrderScheduler;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@DisplayName("Risk settings API")
class RiskSettingsApiTest {

    private static StubFinnhub finnhub;

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RiskSettingsRepository riskSettingsRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PendingOrderScheduler scheduler;
    @Autowired private PlatformTransactionManager transactionManager;
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
        token = signUp("risk@example.com");
        userId = userRepository.findByEmail("risk@example.com").orElseThrow().getId();
        price("100.00");
    }

    @Test
    @DisplayName("a new account has the defaults, and the response describes the bounds and defaults")
    void defaultsAndBounds() throws Exception {
        readLimits().andExpect(status().isOk())
                .andExpect(jsonPath("$.maxPositionPct").value("20.00"))
                .andExpect(jsonPath("$.maxOrderValue").value("5000.00"))
                .andExpect(jsonPath("$.defaultStopLossPct").value("5.00"))
                .andExpect(jsonPath("$.bounds.maxPositionPct.min").value("1.00"))
                .andExpect(jsonPath("$.bounds.maxPositionPct.max").value("100.00"))
                .andExpect(jsonPath("$.bounds.maxOrderValue.min").value("1.00"))
                .andExpect(jsonPath("$.bounds.maxOrderValue.max").value("1000000.00"))
                .andExpect(jsonPath("$.bounds.defaultStopLossPct.min").value("0.50"))
                .andExpect(jsonPath("$.bounds.defaultStopLossPct.max").value("50.00"))
                .andExpect(jsonPath("$.defaults.maxOrderValue").value("5000.00"));
    }

    @Test
    @DisplayName("an update is saved, returned, and read back")
    void update() throws Exception {
        saveLimits("35", "12500.50", "7.5").andExpect(status().isOk())
                .andExpect(jsonPath("$.maxPositionPct").value("35.00"))
                .andExpect(jsonPath("$.maxOrderValue").value("12500.50"))
                .andExpect(jsonPath("$.defaultStopLossPct").value("7.50"));

        readLimits().andExpect(jsonPath("$.maxOrderValue").value("12500.50"));
        RiskSettings saved = riskSettingsRepository.findByUserId(userId).orElseThrow();
        assertThat(saved.getMaxPositionPct()).isEqualByComparingTo("35");
        assertThat(saved.getMaxOrderValue()).isEqualByComparingTo("12500.50");
        assertThat(saved.getDefaultStopLossPct()).isEqualByComparingTo("7.5");
    }

    @Test
    @DisplayName("the bounds themselves are allowed")
    void boundsAreInclusive() throws Exception {
        saveLimits("1", "1", "0.5").andExpect(status().isOk());
        saveLimits("100", "1000000", "50").andExpect(status().isOk())
                .andExpect(jsonPath("$.maxOrderValue").value("1000000.00"));
    }

    @Test
    @DisplayName("a value outside its bounds is 400 naming the field, and nothing is saved")
    void outOfBounds() throws Exception {
        saveLimits("0.99", "5000", "5").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.maxPositionPct").value("must be between 1 and 100"));
        saveLimits("100.01", "5000", "5").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.maxPositionPct").exists());
        saveLimits("20", "0", "5").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.maxOrderValue").value("must be between 1 and 1,000,000"));
        saveLimits("20", "1000001", "5").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.maxOrderValue").exists());
        saveLimits("20", "-50", "5").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.maxOrderValue").exists());
        saveLimits("20", "5000", "0.49").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.defaultStopLossPct").value("must be between 0.5 and 50"));
        saveLimits("20", "5000", "50.01").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.defaultStopLossPct").exists());

        RiskSettings unchanged = riskSettingsRepository.findByUserId(userId).orElseThrow();
        assertThat(unchanged.getMaxPositionPct()).isEqualByComparingTo("20");
        assertThat(unchanged.getMaxOrderValue()).isEqualByComparingTo("5000");
        assertThat(unchanged.getDefaultStopLossPct()).isEqualByComparingTo("5");
    }

    @Test
    @DisplayName("a missing field, too many decimals or a non-number is 400; it is a full replace, never partial")
    void malformed() throws Exception {
        mockMvc.perform(put("/api/risk/settings").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"maxOrderValue\":\"100\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.maxPositionPct").value("is required"))
                .andExpect(jsonPath("$.fieldErrors.defaultStopLossPct").value("is required"));
        saveLimits("20.123", "5000", "5").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.maxPositionPct").value("at most 2 decimal places"));
        saveLimits("20", "5000.999", "5").andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/risk/settings").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxPositionPct\":\"lots\",\"maxOrderValue\":\"5000\",\"defaultStopLossPct\":\"5\"}"))
                .andExpect(status().isBadRequest());
        assertThat(riskSettingsRepository.findByUserId(userId).orElseThrow().getMaxOrderValue()).isEqualByComparingTo("5000");
    }

    @Test
    @DisplayName("a user only ever reads and changes their own limits, and both routes need a token")
    void scopedToTheUser() throws Exception {
        String other = signUp("other@example.com");
        saveLimits("50", "9999", "10").andExpect(status().isOk());

        mockMvc.perform(get("/api/risk/settings").header("Authorization", "Bearer " + other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxPositionPct").value("20.00"))
                .andExpect(jsonPath("$.maxOrderValue").value("5000.00"));

        mockMvc.perform(get("/api/risk/settings")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/risk/settings").contentType(MediaType.APPLICATION_JSON)
                .content("{\"maxPositionPct\":\"50\",\"maxOrderValue\":\"9999\",\"defaultStopLossPct\":\"10\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ---- the limits actually govern trading ----

    @Test
    @DisplayName("a lowered max order value rejects an order that used to pass, and raising it lets the order through")
    void orderValueLimit() throws Exception {
        market(3).andExpect(status().isCreated()); // $300 under the $5,000 default

        saveLimits("20", "250", "5").andExpect(status().isOk());
        market(3).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Order rejected: order too large"));

        saveLimits("20", "5000", "5").andExpect(status().isOk());
        market(3).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("a lowered position limit rejects a buy that would concentrate too much in one stock")
    void positionLimit() throws Exception {
        // $2,000 of a $100,000 portfolio is 2%.
        saveLimits("1", "5000", "5").andExpect(status().isOk());
        market(20).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Order rejected: position limit"));
        market(10).andExpect(status().isCreated()); // exactly 1%

        saveLimits("100", "5000", "5").andExpect(status().isOk());
        market(20).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("the default stop-loss percentage sets where an attached stop-loss is placed")
    void stopLossPercentage() throws Exception {
        saveLimits("20", "5000", "10").andExpect(status().isOk());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"type\":\"MARKET\",\"quantity\":10,\"attachStopLoss\":true}"))
                .andExpect(status().isCreated());

        Order stop = orderRepository.findByUserIdOrderByIdDesc(userId).get(0);
        assertThat(stop.getType()).isEqualTo(OrderType.STOP_LOSS);
        assertThat(stop.getTriggerPrice()).isEqualByComparingTo("90"); // 10% below $100
    }

    @Test
    @DisplayName("a pending order is judged by the limits in force when it triggers, not when it was placed")
    void pendingOrderUsesCurrentLimits() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"type\":\"LIMIT\",\"quantity\":10,\"limitPrice\":\"95.00\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        saveLimits("20", "100", "5").andExpect(status().isOk()); // now far below the order's value
        price("90.00");
        scheduler.runOnce();

        Order order = orderRepository.findByUserIdOrderByIdDesc(userId).get(0);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.getRejectReason()).isEqualTo("order too large");
    }

    @Test
    @DisplayName("an update waits for an order in flight, so once it returns nothing is still checked under the old limits")
    void updateWaitsForTheUserLock() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        // Stands in for an order mid-flight: it holds the user's row lock.
        Future<?> inFlight = pool.submit(() -> transaction.executeWithoutResult(status -> {
            userRepository.findByIdForUpdate(userId).orElseThrow();
            holding.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        assertThat(holding.await(5, TimeUnit.SECONDS)).isTrue();

        ExecutorService caller = Executors.newSingleThreadExecutor();
        try {
            Future<Integer> update = caller.submit(() -> saveLimits("50", "9999", "10").andReturn().getResponse().getStatus());

            assertThatThrownBy(() -> update.get(700, TimeUnit.MILLISECONDS))
                    .as("the update must still be waiting on the lock")
                    .isInstanceOf(TimeoutException.class);
            assertThat(riskSettingsRepository.findByUserId(userId).orElseThrow().getMaxOrderValue()).isEqualByComparingTo("5000");

            release.countDown();
            inFlight.get(5, TimeUnit.SECONDS);
            assertThat(update.get(5, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(riskSettingsRepository.findByUserId(userId).orElseThrow().getMaxOrderValue()).isEqualByComparingTo("9999");
        } finally {
            release.countDown();
            pool.shutdownNow();
            caller.shutdownNow();
        }
    }

    // ---- helpers ----

    private ResultActions readLimits() throws Exception {
        return mockMvc.perform(get("/api/risk/settings").header("Authorization", "Bearer " + token));
    }

    private ResultActions saveLimits(String position, String orderValue, String stopLoss) throws Exception {
        return mockMvc.perform(put("/api/risk/settings")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"maxPositionPct\":\"%s\",\"maxOrderValue\":\"%s\",\"defaultStopLossPct\":\"%s\"}"
                        .formatted(position, orderValue, stopLoss)));
    }

    private ResultActions market(long quantity) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post("/api/orders")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"type\":\"MARKET\",\"quantity\":%d}".formatted(quantity)));
    }

    private void price(String value) {
        finnhub.respondWith(200, "{\"c\":%s,\"d\":0,\"dp\":0,\"h\":%s,\"l\":%s,\"o\":%s,\"pc\":%s,\"t\":1727539200}"
                .formatted(value, value, value, value, value));
        clearCaches();
    }

    private void clearCaches() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
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
