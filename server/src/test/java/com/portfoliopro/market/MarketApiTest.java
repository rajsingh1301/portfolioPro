package com.portfoliopro.market;

import com.portfoliopro.risk.RiskSettingsRepository;
import com.portfoliopro.auth.UserRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@DisplayName("Market API")
class MarketApiTest {

    private static StubFinnhub finnhub;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RiskSettingsRepository riskSettingsRepository;

    @Autowired
    private CacheManager cacheManager;

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
    static void finnhubProperties(DynamicPropertyRegistry registry) {
        // The stub binds to a random port, so the base URL is only known at runtime.
        registry.add("app.finnhub.base-url", () -> finnhub.baseUrl());
    }

    @BeforeEach
    void reset() throws Exception {
        finnhub.reset();
        // The caches outlive a single test because the context is shared.
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        stockRepository.deleteAll();
        riskSettingsRepository.deleteAll();
        userRepository.deleteAll();
        token = signUp();
    }

    @Test
    @DisplayName("a quote is returned with every price as a string")
    void quoteReturnsStrings() throws Exception {
        finnhub.respondWith(200, quoteJson("197.33", "-2.67", "-1.33", "199.00", "196.50", "198.10", "200.00"));

        mockMvc.perform(authed(get("/api/stocks/AAPL/quote")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.price").value("197.33"))
                .andExpect(jsonPath("$.change").value("-2.67"))
                .andExpect(jsonPath("$.previousClose").value("200.00"))
                // isString, not isNumber: a JSON number would reach the browser as a float.
                .andExpect(jsonPath("$.price").isString());
    }

    @Test
    @DisplayName("a half-cent price rounds up rather than being truncated")
    void halfCentPriceRoundsUp() throws Exception {
        finnhub.respondWith(200, quoteJson("8.675", "0.005", "0.06", "8.70", "8.60", "8.67", "8.67"));

        mockMvc.perform(authed(get("/api/stocks/PENNY/quote")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value("8.68"));
    }

    @Test
    @DisplayName("a price beyond double precision survives the parser")
    void pricesKeepDecimalPrecision() throws Exception {
        // This is what USE_BIG_DECIMAL_FOR_FLOATS actually buys. For ordinary prices
        // it changes nothing -- Jackson's DoubleNode.decimalValue() goes through
        // Double.toString, which round-trips any value of up to ~15 significant
        // digits exactly. Past that a double silently loses digits: this value comes
        // back as 9007199254740994.00 without the setting and 9007199254740993.01
        // with it. No stock costs this much, but the parser is shared with everything
        // money-shaped that arrives from upstream later.
        finnhub.respondWith(200, quoteJson(
                "9007199254740993.005", "0.00", "0.00", "1.00", "1.00", "1.00", "1.00"));

        mockMvc.perform(authed(get("/api/stocks/BIG/quote")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value("9007199254740993.01"));
    }

    @Test
    @DisplayName("a symbol Finnhub does not carry is a 404, not an empty quote")
    void unknownSymbolReturns404() throws Exception {
        // Finnhub answers an unknown symbol with zeros and HTTP 200.
        finnhub.respondWith(200, quoteJson("0", "0", "0", "0", "0", "0", "0"));

        mockMvc.perform(authed(get("/api/stocks/NOPE/quote")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Unknown symbol: NOPE"));
    }

    @Test
    @DisplayName("repeated quotes for one symbol cost a single upstream call")
    void quotesAreCached() throws Exception {
        finnhub.respondWith(200, quoteJson("100.00", "1.00", "1.00", "101.00", "99.00", "99.50", "99.00"));

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(authed(get("/api/stocks/AAPL/quote"))).andExpect(status().isOk());
        }

        assertThat(finnhub.requestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("the quote cache expires, so prices do not go stale forever")
    void quoteCacheExpires() throws Exception {
        finnhub.respondWith(200, quoteJson("100.00", "1.00", "1.00", "101.00", "99.00", "99.50", "99.00"));
        mockMvc.perform(authed(get("/api/stocks/AAPL/quote"))).andExpect(status().isOk());

        // The test profile sets the TTL to 2s.
        Thread.sleep(2_500);
        mockMvc.perform(authed(get("/api/stocks/AAPL/quote"))).andExpect(status().isOk());

        assertThat(finnhub.requestCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("an unknown symbol is cached too, so it cannot burn the rate limit")
    void unknownSymbolIsCached() throws Exception {
        finnhub.respondWith(200, quoteJson("0", "0", "0", "0", "0", "0", "0"));

        for (int i = 0; i < 4; i++) {
            mockMvc.perform(authed(get("/api/stocks/NOPE/quote"))).andExpect(status().isNotFound());
        }

        // The cache sits below the 404 check for exactly this reason: @Cacheable does
        // not cache a thrown exception, so caching above it would call out every time.
        assertThat(finnhub.requestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("an upstream failure is a 503, not a 500")
    void upstreamFailureReturns503() throws Exception {
        finnhub.respondWith(500, "{\"error\":\"boom\"}");

        mockMvc.perform(authed(get("/api/stocks/AAPL/quote")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("MARKET_DATA_UNAVAILABLE"));
    }

    @Test
    @DisplayName("an unreadable upstream response is a 503, not a parse error")
    void unreadableUpstreamResponseReturns503() throws Exception {
        finnhub.respondWith(200, "not json at all");

        mockMvc.perform(authed(get("/api/stocks/AAPL/quote")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("MARKET_DATA_UNAVAILABLE"));
    }

    @Test
    @DisplayName("search returns common stocks only and records them")
    void searchFiltersAndPersists() throws Exception {
        finnhub.respondWith(200, """
                {"count":4,"result":[
                  {"description":"Apple Inc","displaySymbol":"AAPL","symbol":"AAPL","type":"Common Stock"},
                  {"description":"Apple Warrant","displaySymbol":"AAPL.WS","symbol":"AAPL.WS","type":"Common Stock"},
                  {"description":"Apple Option","displaySymbol":"AAPLQ","symbol":"AAPLQ","type":"Option"},
                  {"description":"Apple Hospitality","displaySymbol":"APLE","symbol":"aple","type":"Common Stock"}
                ]}""");

        mockMvc.perform(authed(get("/api/stocks/search").param("q", "apple")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].symbol").value("AAPL"))
                // A lowercase upstream symbol is stored uppercase.
                .andExpect(jsonPath("$[1].symbol").value("APLE"))
                // Finnhub's search gives no exchange, so the field is not invented.
                .andExpect(jsonPath("$[0].exchange").doesNotExist());

        assertThat(stockRepository.findById("AAPL")).isPresent();
        assertThat(stockRepository.findById("AAPL.WS")).isEmpty();
        assertThat(stockRepository.count()).isEqualTo(2);
        assertThat(stockRepository.findById("APLE").orElseThrow().getExchange()).isNull();
    }

    @Test
    @DisplayName("a symbol Finnhub lists twice, in any case, appears once and is stored once (JPM came back twice)")
    void duplicateSymbolsInOneSearch() throws Exception {
        finnhub.respondWith(200, """
                {"count":3,"result":[
                  {"description":"JPMorgan Chase & Co","displaySymbol":"JPM","symbol":"JPM","type":"Common Stock"},
                  {"description":"JPMorgan Chase & Co","displaySymbol":"JPM","symbol":"JPM","type":"Common Stock"},
                  {"description":"JPMorgan Chase & Co","displaySymbol":"jpm","symbol":"jpm","type":"Common Stock"}
                ]}""");

        mockMvc.perform(authed(get("/api/stocks/search").param("q", "jpm")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].symbol").value("JPM"));

        assertThat(stockRepository.count()).isEqualTo(1);
        assertThat(stockRepository.findById("JPM").orElseThrow().getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("searching twice does not duplicate rows or call upstream twice")
    void repeatedSearchIsIdempotent() throws Exception {
        finnhub.respondWith(200, """
                {"count":1,"result":[
                  {"description":"Apple Inc","displaySymbol":"AAPL","symbol":"AAPL","type":"Common Stock"}
                ]}""");

        mockMvc.perform(authed(get("/api/stocks/search").param("q", "apple"))).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/stocks/search").param("q", "apple"))).andExpect(status().isOk());

        assertThat(finnhub.requestCount()).isEqualTo(1);
        assertThat(stockRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("both routes refuse an unauthenticated caller before reaching Finnhub")
    void bothRoutesRequireAToken() throws Exception {
        mockMvc.perform(get("/api/stocks/AAPL/quote")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/stocks/search").param("q", "apple")).andExpect(status().isUnauthorized());

        // An anonymous visitor must not be able to spend the app's rate limit.
        assertThat(finnhub.requestCount()).isZero();
    }

    @Test
    @DisplayName("bad parameters are rejected before any upstream call")
    void badParametersAreRejected() throws Exception {
        mockMvc.perform(authed(get("/api/stocks/search").param("q", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.q").isNotEmpty());

        mockMvc.perform(authed(get("/api/stocks/search")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.q").value("is required"));

        mockMvc.perform(authed(get("/api/stocks/search").param("q", "a".repeat(51))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(authed(get("/api/stocks/A B$/quote")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.symbol").isNotEmpty());

        assertThat(finnhub.requestCount()).isZero();
    }

    @Test
    @DisplayName("a lowercase path symbol is normalised before it is used")
    void symbolIsNormalized() throws Exception {
        finnhub.respondWith(200, quoteJson("100.00", "1.00", "1.00", "101.00", "99.00", "99.50", "99.00"));

        mockMvc.perform(authed(get("/api/stocks/msft/quote")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("MSFT"));

        assertThat(finnhub.paths()).anyMatch(path -> path.contains("symbol=MSFT"));
        // The API key travels as a query parameter and must never appear in a response.
        assertThat(finnhub.paths()).allMatch(path -> path.contains("token="));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authed(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder) {
        return builder.header("Authorization", "Bearer " + token);
    }

    private String signUp() throws Exception {
        String response = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"market@example.com\",\"password\":\"Password123\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return response.replaceAll("(?s).*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }

    private static String quoteJson(String c, String d, String dp, String h, String l, String o, String pc) {
        return """
                {"c":%s,"d":%s,"dp":%s,"h":%s,"l":%s,"o":%s,"pc":%s,"t":1727539200}"""
                .formatted(c, d, dp, h, l, o, pc);
    }
}
