package com.portfoliopro.market;

import com.portfoliopro.auth.UserRepository;
import com.portfoliopro.common.exception.MarketDataUnavailableException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@DisplayName("Candles API")
class CandleApiTest {

    private static StubFinnhub twelveData;

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RiskSettingsRepository riskSettingsRepository;
    @Autowired private CacheManager cacheManager;

    private String token;

    @BeforeAll
    static void startStub() throws IOException {
        twelveData = StubFinnhub.start();
    }

    @AfterAll
    static void stopStub() {
        twelveData.stop();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.twelvedata.base-url", () -> twelveData.baseUrl());
    }

    @BeforeEach
    void reset() throws Exception {
        twelveData.reset();
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        riskSettingsRepository.deleteAll();
        userRepository.deleteAll();
        token = signUp();
    }

    @Test
    @DisplayName("daily candles come back oldest first, as strings, with UTC epoch seconds")
    void daily() throws Exception {
        twelveData.respondWith(200, series("2024-01-03", "186.50", "2024-01-02", "185.64"));

        mockMvc.perform(get("/api/stocks/aapl/candles?range=1M").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                // Upstream sent them newest first; the chart library needs ascending.
                .andExpect(jsonPath("$[0].time").value(1704153600L))   // 2024-01-02T00:00:00Z
                .andExpect(jsonPath("$[1].time").value(1704240000L))   // 2024-01-03T00:00:00Z
                .andExpect(jsonPath("$[0].open").value("185.64"))
                .andExpect(jsonPath("$[0].close").value("185.64"))
                .andExpect(jsonPath("$[0].volume").value(1000));
    }

    @Test
    @DisplayName("intraday candles carry a time of day, read as UTC")
    void intraday() throws Exception {
        twelveData.respondWith(200, series("2024-01-02 15:30:00", "100.10"));

        mockMvc.perform(get("/api/stocks/AAPL/candles?range=1D").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].time").value(1704209400L)); // 2024-01-02T15:30:00Z
    }

    @Test
    @DisplayName("the provider is asked for the symbol, the range's interval and size, ascending, in UTC")
    void upstreamRequest() throws Exception {
        twelveData.respondWith(200, series("2024-01-02", "100.00"));

        mockMvc.perform(get("/api/stocks/aapl/candles?range=5Y").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(twelveData.paths()).singleElement().satisfies(path -> assertThat(path)
                .contains("/time_series", "symbol=AAPL", "interval=1week", "outputsize=260", "order=asc", "timezone=UTC")
                .contains("apikey=test-twelve-key"));
    }

    @Test
    @DisplayName("repeat requests are served from the cache; a different range is a separate upstream call")
    void cached() throws Exception {
        twelveData.respondWith(200, series("2024-01-02", "100.00"));

        for (int i = 0; i < 4; i++) {
            mockMvc.perform(get("/api/stocks/AAPL/candles?range=1M").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }
        assertThat(twelveData.requestCount()).isEqualTo(1);

        mockMvc.perform(get("/api/stocks/AAPL/candles?range=1Y").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(twelveData.requestCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("a bar with a missing or unreadable price is dropped rather than drawn wrongly")
    void malformedBarDropped() throws Exception {
        twelveData.respondWith(200, """
                {"meta":{},"status":"ok","values":[
                  {"datetime":"2024-01-02","open":"100","high":"101","low":"99","close":"100.5","volume":"5"},
                  {"datetime":"2024-01-03","open":"n/a","high":"101","low":"99","close":"100.5","volume":"5"},
                  {"datetime":"not a date","open":"100","high":"101","low":"99","close":"100.5","volume":"5"}]}""");

        mockMvc.perform(get("/api/stocks/AAPL/candles").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("the provider's error bodies map to 404 for an unknown symbol and 503 for everything else")
    void providerErrors() throws Exception {
        twelveData.respondWith(200, "{\"code\":404,\"message\":\"symbol not found\",\"status\":\"error\"}");
        mockMvc.perform(get("/api/stocks/NOPE/candles").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        twelveData.respondWith(429, "{\"code\":429,\"message\":\"run out of API credits\",\"status\":\"error\"}");
        mockMvc.perform(get("/api/stocks/AAPL/candles").header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Chart data rate limit reached, try again shortly"));

        twelveData.respondWith(500, "<html>bad gateway</html>");
        mockMvc.perform(get("/api/stocks/AAPL/candles").header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable());

        twelveData.respondWith(500, "{}");
        mockMvc.perform(get("/api/stocks/AAPL/candles").header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("a failure is not cached: the next request goes upstream again and can succeed")
    void failureNotCached() throws Exception {
        twelveData.respondWith(429, "{\"code\":429,\"message\":\"limit\",\"status\":\"error\"}");
        mockMvc.perform(get("/api/stocks/AAPL/candles").header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable());

        twelveData.respondWith(200, series("2024-01-02", "100.00"));
        mockMvc.perform(get("/api/stocks/AAPL/candles").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(twelveData.requestCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("an unknown range or a non-ticker symbol is 400 and never reaches the provider; no token is 401")
    void validation() throws Exception {
        mockMvc.perform(get("/api/stocks/AAPL/candles?range=10Y").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.range").exists());
        mockMvc.perform(get("/api/stocks/AA PL/candles").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/stocks/AAPL/candles")).andExpect(status().isUnauthorized());
        assertThat(twelveData.requestCount()).isZero();
    }

    @Test
    @DisplayName("with no API key configured the client refuses up front instead of calling the provider")
    void noKey() {
        TwelveDataClient client = new TwelveDataClient(twelveData.baseUrl(), "", 1);

        assertThatThrownBy(() -> client.candles("AAPL", CandleRange.MONTH))
                .isInstanceOf(MarketDataUnavailableException.class)
                .hasMessage("Chart data is not configured");
        assertThat(twelveData.requestCount()).isZero();
    }

    // ---- helpers ----

    /** A Twelve Data body from alternating (datetime, price) pairs, in the order given. */
    private static String series(String... pairs) {
        StringBuilder values = new StringBuilder();
        for (int i = 0; i < pairs.length; i += 2) {
            if (i > 0) values.append(',');
            values.append("""
                    {"datetime":"%s","open":"%s","high":"%s","low":"%s","close":"%s","volume":"1000"}"""
                    .formatted(pairs[i], pairs[i + 1], pairs[i + 1], pairs[i + 1], pairs[i + 1]));
        }
        return "{\"meta\":{\"symbol\":\"AAPL\"},\"status\":\"ok\",\"values\":[" + values + "]}";
    }

    private String signUp() throws Exception {
        String response = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"chart@example.com\",\"password\":\"Password123\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return response.replaceAll("(?s).*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }
}
