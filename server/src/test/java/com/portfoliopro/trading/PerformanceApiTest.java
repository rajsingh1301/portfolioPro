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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * History is made the way it is made for real: orders through the API. The dates are then
 * moved back with SQL, since a test cannot wait days. Every expected figure is worked out by
 * hand in the comments, from the closes in {@link #candles}.
 */
@IntegrationTest
@DisplayName("Performance API")
class PerformanceApiTest {

    private static StubFinnhub finnhub;
    private static StubFinnhub twelveData;

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RiskSettingsRepository riskSettingsRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private CacheManager cacheManager;

    private final LocalDate today = LocalDate.now(ZoneOffset.UTC);
    private String token;
    private Long userId;

    @BeforeAll
    static void startStubs() throws IOException {
        finnhub = StubFinnhub.start();
        twelveData = StubFinnhub.start();
    }

    @AfterAll
    static void stopStubs() {
        finnhub.stop();
        twelveData.stop();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.finnhub.base-url", () -> finnhub.baseUrl());
        registry.add("app.twelvedata.base-url", () -> twelveData.baseUrl());
    }

    @BeforeEach
    void reset() throws Exception {
        finnhub.reset();
        twelveData.reset();
        clearCaches();
        riskSettingsRepository.deleteAll();
        userRepository.deleteAll();
        token = signUp("perf@example.com");
        userId = userRepository.findByEmail("perf@example.com").orElseThrow().getId();
        RiskSettings settings = riskSettingsRepository.findByUserId(userId).orElseThrow();
        settings.setMaxPositionPct(new BigDecimal("100.00"));
        settings.setMaxOrderValue(new BigDecimal("1000000"));
        riskSettingsRepository.save(settings);
    }

    @Test
    @DisplayName("a new account with no trades has one point, no movement, and nothing to attribute")
    void noTrades() throws Exception {
        performance("3M").andExpect(status().isOk())
                .andExpect(jsonPath("$.points.length()").value(1))
                .andExpect(jsonPath("$.points[0].value").value("100000.00"))
                .andExpect(jsonPath("$.summary.startValue").value("100000.00"))
                .andExpect(jsonPath("$.summary.change").value("0.00"))
                .andExpect(jsonPath("$.summary.changePercent").value("0.00"))
                .andExpect(jsonPath("$.summary.bestDay").doesNotExist())
                .andExpect(jsonPath("$.summary.worstDay").doesNotExist())
                .andExpect(jsonPath("$.positions.length()").value(0))
                .andExpect(jsonPath("$.estimatedSymbols.length()").value(0));
    }

    @Test
    @DisplayName("the value each day is cash plus shares at that day's close, with today taken live")
    void dailyValues() throws Exception {
        twoSymbolHistory();

        String body = performance("3M").andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value(today.minusDays(10).toString()))
                .andExpect(jsonPath("$.to").value(today.toString()))
                .andExpect(jsonPath("$.points.length()").value(11))
                .andReturn().getResponse().getContentAsString();

        // Worked out by hand. Cash: 100000 until day -8 (buy 10 AAPL @ 100 -> 99000), day -4 (buy 5 MSFT
        // @ 200 -> 98000), day -2 (sell 4 AAPL @ 120 -> 98480). Shares x that day's close:
        //   -10: 100000            -9: 100000            -8: 99000 + 10x100        = 100000
        //   -7: 99000 + 10x102     -6: 99000 + 10x104    -5: 99000 + 10x106        = 100020, 100040, 100060
        //   -4: 98000 + 10x108 + 5x200                                              = 100080
        //   -3: 98000 + 10x110 + 5x198                                              = 100090
        //   -2: 98480 + 6x120 + 5x196   -1: 98480 + 6x125 + 5x192                   = 100180, 100190
        //    0: today, live: 98480 + 6x130 + 5x190                                  = 100210
        List<String> expected = List.of("100000.00", "100000.00", "100000.00", "100020.00", "100040.00",
                "100060.00", "100080.00", "100090.00", "100180.00", "100190.00", "100210.00");
        List<String> actual = JsonPath.read(body, "$.points[*].value");
        assertThat(actual).isEqualTo(expected);

        List<String> dates = JsonPath.read(body, "$.points[*].date");
        assertThat(dates.get(0)).isEqualTo(today.minusDays(10).toString());
        assertThat(dates.get(10)).isEqualTo(today.toString());

        // Day -2 on its own: cash 98480, invested 6x120 + 5x196 = 1700.
        assertThat((String) JsonPath.read(body, "$.points[8].cash")).isEqualTo("98480.00");
        assertThat((String) JsonPath.read(body, "$.points[8].invested")).isEqualTo("1700.00");
    }

    @Test
    @DisplayName("the summary gives the change over the range and the best day, and no worst day when nothing fell")
    void summary() throws Exception {
        twoSymbolHistory();

        // Start 100000, end 100210: +210.00, and 210 / 100000 = 0.21%. The biggest single step
        // is day -2 (100090 -> 100180 = +90.00, which is 0.0899...% of 100090, so 0.09%).
        performance("3M").andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.startValue").value("100000.00"))
                .andExpect(jsonPath("$.summary.endValue").value("100210.00"))
                .andExpect(jsonPath("$.summary.change").value("210.00"))
                .andExpect(jsonPath("$.summary.changePercent").value("0.21"))
                .andExpect(jsonPath("$.summary.bestDay.date").value(today.minusDays(2).toString()))
                .andExpect(jsonPath("$.summary.bestDay.change").value("90.00"))
                .andExpect(jsonPath("$.summary.bestDay.changePercent").value("0.09"))
                .andExpect(jsonPath("$.summary.worstDay").doesNotExist());
    }

    @Test
    @DisplayName("a losing stretch shows as a worst day with a negative change")
    void worstDay() throws Exception {
        // Buy 10 XYZ @ 100 three days ago. Closes 100, 90, 95, then 95 live today.
        price("XYZ", "100");
        long order = place("XYZ", "BUY", 10);
        backdate(order, 3);
        setUserCreated(3);
        twelveData.respondForSymbol("XYZ", candles(3, "100", 2, "90", 1, "95", 0, "95"));
        price("XYZ", "95");
        clearCaches();

        // -3: 99000 + 10x100 = 100000; -2: 99000 + 900 = 99900 (-100); -1: 99000 + 950 = 99950 (+50); 0: same.
        performance("3M").andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.worstDay.date").value(today.minusDays(2).toString()))
                .andExpect(jsonPath("$.summary.worstDay.change").value("-100.00"))
                .andExpect(jsonPath("$.summary.worstDay.changePercent").value("-0.10"))
                .andExpect(jsonPath("$.summary.bestDay.date").value(today.minusDays(1).toString()))
                .andExpect(jsonPath("$.summary.bestDay.change").value("50.00"))
                .andExpect(jsonPath("$.summary.change").value("-50.00"));
    }

    @Test
    @DisplayName("each position's result is realized plus unrealized, best first, and a closed position still counts")
    void positions() throws Exception {
        twoSymbolHistory();
        // A third, fully closed position: buy 5 ZED @ 100, sell 5 @ 110 -> realized +50.
        price("ZED", "100");
        long buy = place("ZED", "BUY", 5);
        price("ZED", "110");
        long sell = place("ZED", "SELL", 5);
        backdate(buy, 6);
        backdate(sell, 5);
        price("AAPL", "130");
        price("MSFT", "190");
        clearCaches();

        // AAPL: sold 4 @ 120 vs cost 100 = +80 realized; 6 held, 130 now vs 100 = +180 unrealized; total +260.
        // MSFT: 5 held, 190 now vs 200 = -50 unrealized. ZED: closed, +50 realized.
        performance("3M").andExpect(status().isOk())
                .andExpect(jsonPath("$.positions.length()").value(3))
                .andExpect(jsonPath("$.positions[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$.positions[0].open").value(true))
                .andExpect(jsonPath("$.positions[0].quantity").value(6))
                .andExpect(jsonPath("$.positions[0].realizedPnl").value("80.00"))
                .andExpect(jsonPath("$.positions[0].unrealizedPnl").value("180.00"))
                .andExpect(jsonPath("$.positions[0].totalPnl").value("260.00"))
                .andExpect(jsonPath("$.positions[1].symbol").value("ZED"))
                .andExpect(jsonPath("$.positions[1].open").value(false))
                .andExpect(jsonPath("$.positions[1].quantity").value(0))
                .andExpect(jsonPath("$.positions[1].totalPnl").value("50.00"))
                .andExpect(jsonPath("$.positions[1].unrealizedPnl").doesNotExist())
                .andExpect(jsonPath("$.positions[2].symbol").value("MSFT"))
                .andExpect(jsonPath("$.positions[2].totalPnl").value("-50.00"));
    }

    @Test
    @DisplayName("without price history the series is still returned, valued at trade prices, and says which symbols")
    void noPriceHistory() throws Exception {
        twoSymbolHistory();
        twelveData.reset();
        twelveData.respondWith(429, "{\"code\":429,\"message\":\"limit\",\"status\":\"error\"}");
        clearCaches();

        // No closes at all, so each symbol is worth its last trade price: AAPL 100 until the day -2
        // sale at 120, MSFT 200. Day -5: 99000 + 10x100 = 100000. Day -3: 98000 + 1000 + 5x200 = 100000.
        // Day -2: 98480 + 6x120 + 5x200 = 100200. Today is live and unchanged: 100210.
        String body = performance("3M").andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedSymbols.length()").value(2))
                .andExpect(jsonPath("$.estimatedSymbols[0]").value("AAPL"))
                .andExpect(jsonPath("$.estimatedSymbols[1]").value("MSFT"))
                .andReturn().getResponse().getContentAsString();
        assertThat(valueOn(body, today.minusDays(5))).isEqualTo("100000.00");
        assertThat(valueOn(body, today.minusDays(3))).isEqualTo("100000.00");
        assertThat(valueOn(body, today.minusDays(2))).isEqualTo("100200.00");
        assertThat(valueOn(body, today)).isEqualTo("100210.00");
    }

    @Test
    @DisplayName("today is valued live, so the last point is exactly the total at the top of the page")
    void todayIsLive() throws Exception {
        twoSymbolHistory();
        // Today's close in the history is 130, but the live quote has moved to 140.
        price("AAPL", "140");

        // 98480 cash + 6 x 140 + 5 x 190 = 98480 + 840 + 950
        String body = performance("3M").andExpect(status().isOk())
                .andExpect(jsonPath("$.points[10].value").value("100270.00"))
                .andExpect(jsonPath("$.summary.endValue").value("100270.00"))
                .andReturn().getResponse().getContentAsString();

        String portfolio = mockMvc.perform(get("/api/portfolio").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(portfolio, "$.totalValue")).isEqualTo(JsonPath.read(body, "$.points[10].value"));
        assertThat((String) JsonPath.read(portfolio, "$.cash")).isEqualTo(JsonPath.read(body, "$.points[10].cash"));
    }

    @Test
    @DisplayName("price history is fetched for at most eight symbols; the rest are valued at trade prices and say so")
    void historyIsCapped() throws Exception {
        twelveData.respondWith(200, candles(0, "10.00"));
        price("S1", "10.00");
        for (int i = 1; i <= 9; i++) {
            price("S" + i, "10.00");
            place("S" + i, "BUY", 1);
        }
        clearCaches();
        twelveData.resetCounts();

        // Nine symbols, one trade each, so ties break alphabetically: S1 to S8 get history, S9 does not.
        performance("3M").andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedSymbols.length()").value(1))
                .andExpect(jsonPath("$.estimatedSymbols[0]").value("S9"));
        assertThat(twelveData.requestCount()).isEqualTo(PerformanceService.MAX_HISTORY_SYMBOLS);
    }

    @Test
    @DisplayName("a range longer than the account is clamped to the day it was opened")
    void clampedToAccountStart() throws Exception {
        twoSymbolHistory();

        performance("1Y").andExpect(status().isOk())
                .andExpect(jsonPath("$.range").value("1Y"))
                .andExpect(jsonPath("$.from").value(today.minusDays(10).toString()));
        performance("1M").andExpect(jsonPath("$.from").value(today.minusDays(10).toString()));
    }

    @Test
    @DisplayName("one user's history never includes another's, and it needs a token and a valid range")
    void scopedAndValidated() throws Exception {
        twoSymbolHistory();
        String other = signUp("other@example.com");

        mockMvc.perform(get("/api/portfolio/performance").header("Authorization", "Bearer " + other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points.length()").value(1))
                .andExpect(jsonPath("$.positions.length()").value(0))
                .andExpect(jsonPath("$.summary.change").value("0.00"));

        mockMvc.perform(get("/api/portfolio/performance")).andExpect(status().isUnauthorized());
        performance("10Y").andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.range").exists());
    }

    // ---- fixtures ----

    /**
     * AAPL: buy 10 @ 100 on day -8, sell 4 @ 120 on day -2. MSFT: buy 5 @ 200 on day -4. The account was
     * opened on day -10. Closes are set per day; today's live quotes are AAPL 130 and MSFT 190.
     */
    private void twoSymbolHistory() throws Exception {
        price("AAPL", "100");
        long aaplBuy = place("AAPL", "BUY", 10);
        price("MSFT", "200");
        long msftBuy = place("MSFT", "BUY", 5);
        price("AAPL", "120");
        long aaplSell = place("AAPL", "SELL", 4);
        backdate(aaplBuy, 8);
        backdate(msftBuy, 4);
        backdate(aaplSell, 2);
        setUserCreated(10);

        twelveData.respondForSymbol("AAPL", candles(
                10, "98", 9, "99", 8, "100", 7, "102", 6, "104", 5, "106", 4, "108", 3, "110", 2, "120", 1, "125", 0, "130"));
        twelveData.respondForSymbol("MSFT", candles(
                10, "200", 9, "200", 8, "200", 7, "200", 6, "200", 5, "200", 4, "200", 3, "198", 2, "196", 1, "192", 0, "190"));
        price("AAPL", "130");
        price("MSFT", "190");
        clearCaches();
    }

    /** A Twelve Data body, newest first as the provider sends it, from pairs of (days ago, close). */
    private String candles(Object... daysAgoAndClose) {
        Map<Integer, String> closesByDaysAgo = new LinkedHashMap<>();
        for (int i = 0; i < daysAgoAndClose.length; i += 2) {
            closesByDaysAgo.put((Integer) daysAgoAndClose[i], (String) daysAgoAndClose[i + 1]);
        }
        Map<Integer, String> ordered = new LinkedHashMap<>();
        closesByDaysAgo.keySet().stream().sorted().forEach(days -> ordered.put(days, closesByDaysAgo.get(days)));
        StringBuilder values = new StringBuilder();
        ordered.forEach((days, close) -> {
            if (values.length() > 0) values.append(',');
            values.append("{\"datetime\":\"%s\",\"open\":\"%s\",\"high\":\"%s\",\"low\":\"%s\",\"close\":\"%s\",\"volume\":\"1000\"}"
                    .formatted(today.minusDays(days), close, close, close, close));
        });
        return "{\"meta\":{},\"status\":\"ok\",\"values\":[" + values + "]}";
    }

    private void price(String symbol, String value) {
        finnhub.respondForSymbol(symbol,
                "{\"c\":%s,\"d\":0,\"dp\":0,\"h\":%s,\"l\":%s,\"o\":%s,\"pc\":%s,\"t\":1727539200}"
                        .formatted(value, value, value, value, value));
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

    private void setUserCreated(int daysAgo) {
        String at = today.minusDays(daysAgo) + " 09:00:00";
        jdbc.update("UPDATE users SET created_at = ? WHERE id = ?", at, userId);
        jdbc.update("UPDATE cash_transactions SET created_at = ? WHERE user_id = ? AND order_id IS NULL", at, userId);
    }

    /** The value on one day: a filter in JsonPath always answers with a list, even for one match. */
    private static String valueOn(String body, LocalDate day) {
        List<String> matches = JsonPath.read(body, "$.points[?(@.date=='" + day + "')].value");
        assertThat(matches).hasSize(1);
        return matches.get(0);
    }

    private ResultActions performance(String range) throws Exception {
        return mockMvc.perform(get("/api/portfolio/performance?range=" + range).header("Authorization", "Bearer " + token));
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
