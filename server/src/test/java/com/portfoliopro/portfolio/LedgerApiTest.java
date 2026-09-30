package com.portfoliopro.portfolio;

import com.portfoliopro.auth.UserRepository;
import com.portfoliopro.market.StubFinnhub;
import com.portfoliopro.risk.RiskSettings;
import com.portfoliopro.risk.RiskSettingsRepository;
import com.portfoliopro.support.IntegrationTest;
import com.portfoliopro.trading.PendingOrderScheduler;
import com.portfoliopro.trading.TradeRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The cash ledger is only worth having if it reconciles: its amounts add up to the
 * balance, and every row's running total is right. These tests check that as an
 * invariant after real activity, rather than a row at a time.
 */
@IntegrationTest
@DisplayName("Cash ledger")
class LedgerApiTest {

    private static StubFinnhub finnhub;

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RiskSettingsRepository riskSettingsRepository;
    @Autowired private CashTransactionRepository ledgerRepository;
    @Autowired private TradeRepository tradeRepository;
    @Autowired private PendingOrderScheduler scheduler;
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
        token = signUp("ledger@example.com");
        userId = userRepository.findByEmail("ledger@example.com").orElseThrow().getId();
        RiskSettings settings = riskSettingsRepository.findByUserId(userId).orElseThrow();
        settings.setMaxPositionPct(new BigDecimal("100.00"));
        settings.setMaxOrderValue(new BigDecimal("1000000"));
        riskSettingsRepository.save(settings);
        price("100.00");
    }

    @Test
    @DisplayName("a new account's ledger is exactly its opening deposit, matching the balance")
    void openingDeposit() {
        List<CashTransaction> ledger = ledgerRepository.findByUserIdOrderByIdAsc(userId);

        assertThat(ledger).singleElement().satisfies(row -> {
            assertThat(row.getType()).isEqualTo(CashTransactionType.DEPOSIT);
            assertThat(row.getAmount()).isEqualByComparingTo("100000");
            assertThat(row.getBalanceAfter()).isEqualByComparingTo("100000");
            assertThat(row.getOrderId()).isNull();
        });
        assertThat(cash()).isEqualByComparingTo("100000");
    }

    @Test
    @DisplayName("after buys, sells, a rejection and a scheduled fill, the ledger sums to the balance row by row")
    void reconcilesAfterActivity() throws Exception {
        order("BUY", "MARKET", 10, null).andExpect(status().isCreated());
        price("120.00");
        order("SELL", "MARKET", 4, null).andExpect(status().isCreated());
        order("SELL", "MARKET", 999, null).andExpect(status().isUnprocessableEntity()); // rejected: no cash moves
        order("BUY", "LIMIT", 5, "115.00").andExpect(status().isCreated());              // pending: no cash moves yet
        assertThat(ledgerRepository.findByUserIdOrderByIdAsc(userId)).hasSize(3);       // deposit, buy, sell

        price("110.00");
        scheduler.runOnce();                                                             // the limit buy fills

        List<CashTransaction> ledger = ledgerRepository.findByUserIdOrderByIdAsc(userId);
        assertThat(ledger).extracting(CashTransaction::getType).containsExactly(
                CashTransactionType.DEPOSIT, CashTransactionType.BUY, CashTransactionType.SELL, CashTransactionType.BUY);

        BigDecimal running = BigDecimal.ZERO;
        for (CashTransaction row : ledger) {
            running = running.add(row.getAmount());
            assertThat(row.getBalanceAfter()).as("running total at row %s", row.getType()).isEqualByComparingTo(running);
        }
        assertThat(running).isEqualByComparingTo(cash());
        // 100000 - 10x100 + 4x120 - 5x110
        assertThat(cash()).isEqualByComparingTo("98930");
    }

    @Test
    @DisplayName("every trade has exactly one ledger row and only the deposit is unlinked from an order")
    void everyTradeIsOnTheLedger() throws Exception {
        order("BUY", "MARKET", 3, null).andExpect(status().isCreated());
        order("BUY", "MARKET", 2, null).andExpect(status().isCreated());
        order("SELL", "MARKET", 1, null).andExpect(status().isCreated());

        List<CashTransaction> ledger = ledgerRepository.findByUserIdOrderByIdAsc(userId);
        assertThat(ledger.stream().filter(row -> row.getType() != CashTransactionType.DEPOSIT).count())
                .isEqualTo(tradeRepository.count());
        assertThat(ledger.stream().filter(row -> row.getOrderId() == null)).singleElement()
                .satisfies(row -> assertThat(row.getType()).isEqualTo(CashTransactionType.DEPOSIT));
    }

    // ---- helpers ----

    private ResultActions order(String side, String type, long quantity, String limit) throws Exception {
        String body = "{\"symbol\":\"AAPL\",\"side\":\"%s\",\"type\":\"%s\",\"quantity\":%d%s}".formatted(
                side, type, quantity, limit == null ? "" : ",\"limitPrice\":\"" + limit + "\"");
        return mockMvc.perform(MockMvcRequestBuilders.post("/api/orders")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
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

    private String signUp(String email) throws Exception {
        String response = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"Password123\"}".formatted(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return response.replaceAll("(?s).*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }
}
