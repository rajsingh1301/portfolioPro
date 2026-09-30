package com.portfoliopro.trading;

import com.jayway.jsonpath.JsonPath;
import com.portfoliopro.auth.UserRepository;
import com.portfoliopro.market.StubFinnhub;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real orders through the API, with the dates then moved back in SQL, against quotes that carry a
 * previous close and the time they were struck. Every expected figure is worked out by hand.
 */
@IntegrationTest
@DisplayName("Day P&L API")
class TodayApiTest {

    private static StubFinnhub finnhub;

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RiskSettingsRepository riskSettingsRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private CacheManager cacheManager;

    private final LocalDate today = LocalDate.now(ZoneOffset.UTC);
    private String token;

    @BeforeAll
    static void startStub() throws IOException {
        finnhub = StubFinnhub.start();
    }

    @AfterAll
    static void stopStub() {
        finnhub.stop();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.finnhub.base-url", () -> finnhub.baseUrl());
    }

    @BeforeEach
    void reset() throws Exception {
        finnhub.reset();
        clearCaches();
        riskSettingsRepository.deleteAll();
        userRepository.deleteAll();
        token = signUp("today@example.com");
        Long userId = userRepository.findByEmail("today@example.com").orElseThrow().getId();
        RiskSettings settings = riskSettingsRepository.findByUserId(userId).orElseThrow();
        settings.setMaxPositionPct(new BigDecimal("100.00"));
        settings.setMaxOrderValue(new BigDecimal("1000000"));
        riskSettingsRepository.save(settings);
    }

    @Test
    @DisplayName("an account with nothing held and nothing traded has made nothing today")
    void nothing() throws Exception {
        today().andExpect(status().isOk())
                .andExpect(jsonPath("$.dayPnl").value("0.00"))
                .andExpect(jsonPath("$.dayPnlPercent").value("0.00"))
                .andExpect(jsonPath("$.positions.length()").value(0));
    }

    @Test
    @DisplayName("old shares are measured from the previous close, new ones from the price paid")
    void oldAndNewShares() throws Exception {
        // AAPL: 10 bought yesterday at 100, 5 more today at 104. MSFT: 3 bought today at 200.
        quote("AAPL", "100", "100", now());
        long yesterdaysBuy = place("AAPL", "BUY", 10);
        backdate(yesterdaysBuy, 1);
        quote("AAPL", "104", "100", now());
        place("AAPL", "BUY", 5);
        quote("MSFT", "200", "195", now());
        place("MSFT", "BUY", 3);
        // Now: AAPL 105 (previous close 100), MSFT 190 (previous close 195).
        quote("AAPL", "105", "100", now());
        quote("MSFT", "190", "195", now());

        // AAPL: 15 x 105 - 10 x 100 - 5 x 104 = 1575 - 1000 - 520 = +55
        //       (the 10 old shares +5 each = 50, the 5 new ones 105 - 104 = +1 each = 5).
        // MSFT: 3 x 190 - 0 - 3 x 200 = -30 (a naive 3 x (190 - 195) would say -15).
        // Total +25. The portfolio is worth 97880 cash + 15x105 + 3x190 = 100025 now, so 100000 at the
        // close, and 25 / 100000 = 0.025%, which rounds half up to 0.03.
        today().andExpect(status().isOk())
                .andExpect(jsonPath("$.positions.length()").value(2))
                .andExpect(jsonPath("$.positions[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$.positions[0].quantity").value(15))
                .andExpect(jsonPath("$.positions[0].previousClose").value("100.00"))
                .andExpect(jsonPath("$.positions[0].dayChange").value("5.00"))
                .andExpect(jsonPath("$.positions[0].dayChangePercent").value("5.00"))
                .andExpect(jsonPath("$.positions[0].dayPnl").value("55.00"))
                .andExpect(jsonPath("$.positions[1].symbol").value("MSFT"))
                .andExpect(jsonPath("$.positions[1].dayChange").value("-5.00"))
                .andExpect(jsonPath("$.positions[1].dayChangePercent").value("-2.56"))
                .andExpect(jsonPath("$.positions[1].dayPnl").value("-30.00"))
                .andExpect(jsonPath("$.dayPnl").value("25.00"))
                .andExpect(jsonPath("$.dayPnlPercent").value("0.03"));
    }

    @Test
    @DisplayName("a position opened and closed today counts what the round trip made, though nothing is held")
    void roundTripToday() throws Exception {
        quote("KO", "100", "97", now());
        place("KO", "BUY", 5);
        quote("KO", "110", "97", now());
        place("KO", "SELL", 5);
        quote("KO", "108", "97", now());

        // Bought 5 at 100 and sold 5 at 110: +50. The price it has moved to since is irrelevant.
        today().andExpect(status().isOk())
                .andExpect(jsonPath("$.positions.length()").value(1))
                .andExpect(jsonPath("$.positions[0].symbol").value("KO"))
                .andExpect(jsonPath("$.positions[0].quantity").value(0))
                .andExpect(jsonPath("$.positions[0].dayPnl").value("50.00"))
                .andExpect(jsonPath("$.dayPnl").value("50.00"));
    }

    @Test
    @DisplayName("the session is the one the quote belongs to: an order at yesterday's close has made nothing yet")
    void sessionFollowsTheQuote() throws Exception {
        // Two shares from three days ago, then three more bought now, at a close struck yesterday
        // (as happens when the market is shut): price 100, previous close 97.
        Instant closeStruck = Instant.now().minusSeconds(24 * 3600);
        quote("AAPL", "100", "97", closeStruck);
        long old = place("AAPL", "BUY", 2);
        backdate(old, 3);
        // One more share bought yesterday at midday, inside the session the quote belongs to.
        long yesterday = place("AAPL", "BUY", 1);
        backdate(yesterday, 1);
        place("AAPL", "BUY", 3);

        // The 2 old shares moved 100 - 97 = +3 each = 6. The 1 bought yesterday and the 3 bought now
        // were both bought at that same 100, so 0 each. Total 6:
        //   6 x 100 - 2 x 97 - (1 + 3) x 100 = 600 - 194 - 400.
        // Were "today" taken as the calendar day rather than the quote's session, the share bought
        // yesterday would count as held at the previous close and add 1 x (100 - 97) = 3 more.
        today().andExpect(status().isOk())
                .andExpect(jsonPath("$.positions[0].quantity").value(6))
                .andExpect(jsonPath("$.positions[0].dayPnl").value("6.00"))
                .andExpect(jsonPath("$.dayPnl").value("6.00"));
    }

    @Test
    @DisplayName("a position that was closed before this session, and not touched since, is not listed")
    void oldClosedPositionsStayOut() throws Exception {
        quote("KO", "100", "97", now());
        long buy = place("KO", "BUY", 5);
        long sell = place("KO", "SELL", 5);
        backdate(buy, 3);
        backdate(sell, 2);

        today().andExpect(status().isOk())
                .andExpect(jsonPath("$.positions.length()").value(0))
                .andExpect(jsonPath("$.dayPnl").value("0.00"));
    }

    @Test
    @DisplayName("a symbol whose quote fails is listed without figures and left out of the total")
    void unpriced() throws Exception {
        quote("AAPL", "100", "100", now());
        place("AAPL", "BUY", 4);
        finnhub.reset();
        finnhub.respondWith(500, "{}");
        clearCaches();

        today().andExpect(status().isOk())
                .andExpect(jsonPath("$.positions.length()").value(1))
                .andExpect(jsonPath("$.positions[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$.positions[0].quantity").value(4))
                .andExpect(jsonPath("$.positions[0].dayPnl").doesNotExist())
                .andExpect(jsonPath("$.dayPnl").value("0.00"));
    }

    @Test
    @DisplayName("one user's day never includes another's, and it needs a token")
    void scopedToTheUser() throws Exception {
        quote("AAPL", "100", "100", now());
        place("AAPL", "BUY", 4);
        quote("AAPL", "110", "100", now());
        String other = signUp("other@example.com");

        mockMvc.perform(get("/api/portfolio/today").header("Authorization", "Bearer " + other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positions.length()").value(0))
                .andExpect(jsonPath("$.dayPnl").value("0.00"));
        mockMvc.perform(get("/api/portfolio/today")).andExpect(status().isUnauthorized());
    }

    // ---- helpers ----

    private Instant now() {
        return Instant.now();
    }

    private void quote(String symbol, String price, String previousClose, Instant struck) {
        finnhub.respondForSymbol(symbol,
                "{\"c\":%s,\"d\":0,\"dp\":0,\"h\":%s,\"l\":%s,\"o\":%s,\"pc\":%s,\"t\":%d}"
                        .formatted(price, price, price, price, previousClose, struck.getEpochSecond()));
        clearCaches();
    }

    private long place(String symbol, String side, long quantity) throws Exception {
        String response = mockMvc.perform(MockMvcRequestBuilders.post("/api/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"%s\",\"side\":\"%s\",\"type\":\"MARKET\",\"quantity\":%d}".formatted(symbol, side, quantity)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    /** Moves an order's trade and ledger row back in time. A string, so no time zone is applied to it. */
    private void backdate(long orderId, int daysAgo) {
        String at = today.minusDays(daysAgo) + " 12:00:00";
        jdbc.update("UPDATE trades SET executed_at = ? WHERE order_id = ?", at, orderId);
        jdbc.update("UPDATE cash_transactions SET created_at = ? WHERE order_id = ?", at, orderId);
    }

    private ResultActions today() throws Exception {
        return mockMvc.perform(get("/api/portfolio/today").header("Authorization", "Bearer " + token));
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
