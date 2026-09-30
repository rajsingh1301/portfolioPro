package com.portfoliopro.analysis;

import com.portfoliopro.auth.UserRepository;
import com.portfoliopro.market.StubFinnhub;
import com.portfoliopro.market.Stock;
import com.portfoliopro.market.StockRepository;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Random;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@DisplayName("Analysis API")
class AnalysisApiTest {

    private static StubFinnhub finnhub;
    private static StubFinnhub twelveData;

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RiskSettingsRepository riskSettingsRepository;
    @Autowired private StockRepository stockRepository;
    @Autowired private CacheManager cacheManager;

    private String token;

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
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        stockRepository.deleteAll();
        riskSettingsRepository.deleteAll();
        userRepository.deleteAll();
        token = signUp();
    }

    // ---- indicators ----

    @Test
    @DisplayName("each series has a point per candle from where its window is full, prices to 4 places and RSI to 2")
    void seriesShapes() throws Exception {
        double[] closes = walk(252);
        twelveData.respondWith(200, daily(closes));

        indicators("AAPL", "1Y").andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.range").value("1Y"))
                .andExpect(jsonPath("$.sma20.length()").value(252 - 19))
                .andExpect(jsonPath("$.sma50.length()").value(252 - 49))
                .andExpect(jsonPath("$.ema20.length()").value(252 - 20))
                .andExpect(jsonPath("$.rsi14.length()").value(252 - 14))
                .andExpect(jsonPath("$.macd.line.length()").value(252 - 26))
                .andExpect(jsonPath("$.macd.signal.length()").value(252 - 34))
                .andExpect(jsonPath("$.macd.histogram.length()").value(252 - 34))
                .andExpect(jsonPath("$.bollinger.upper.length()").value(252 - 19))
                .andExpect(jsonPath("$.disclaimer").value(IndicatorService.DISCLAIMER))
                .andExpect(jsonPath("$.sma20[0].value").value(org.hamcrest.Matchers.matchesPattern("-?\\d+\\.\\d{4}")))
                .andExpect(jsonPath("$.rsi14[0].value").value(org.hamcrest.Matchers.matchesPattern("\\d+\\.\\d{2}")));
    }

    @Test
    @DisplayName("the last SMA(20) is the mean of the last 20 closes, and points carry the candle's own time")
    void smaValueAndTime() throws Exception {
        double[] closes = walk(252);
        twelveData.respondWith(200, daily(closes));

        double expected = 0;
        for (int i = 232; i < 252; i++) expected += closes[i];
        expected /= 20;

        String body = indicators("AAPL", "1Y").andReturn().getResponse().getContentAsString();
        var last = com.jayway.jsonpath.JsonPath.<java.util.List<java.util.Map<String, Object>>>read(body, "$.sma20");
        var point = last.get(last.size() - 1);
        assertThat(Double.parseDouble((String) point.get("value"))).isCloseTo(expected, org.assertj.core.data.Offset.offset(1e-3));
        assertThat(((Number) point.get("time")).longValue()).isEqualTo(START.plus(251, ChronoUnit.DAYS).getEpochSecond());
    }

    @Test
    @DisplayName("a 1M request is computed on the year of daily candles then cut to a month, in one provider call")
    void shortRangeUsesTheYearSeries() throws Exception {
        twelveData.respondWith(200, daily(walk(252)));

        // A month of candles is 22 bars, too few for a 50-bar average on its own. Computed
        // on the year and cut back, every average in the month still has a value.
        indicators("AAPL", "1M").andExpect(status().isOk())
                .andExpect(jsonPath("$.sma50.length()").value(22))
                .andExpect(jsonPath("$.sma20.length()").value(22))
                .andExpect(jsonPath("$.macd.signal.length()").value(22))
                .andExpect(jsonPath("$.rsi14.length()").value(22));

        assertThat(twelveData.paths()).singleElement().satisfies(path -> assertThat(path)
                .contains("interval=1day", "outputsize=252"));

        // The same year of candles serves the other daily ranges from the cache.
        indicators("AAPL", "6M").andExpect(jsonPath("$.sma50.length()").value(130));
        indicators("AAPL", "1Y").andExpect(status().isOk());
        assertThat(twelveData.requestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("an intraday range is computed on its own candles")
    void intradayRange() throws Exception {
        twelveData.respondWith(200, intraday(walk(80)));

        indicators("AAPL", "1D").andExpect(status().isOk())
                .andExpect(jsonPath("$.rsi14.length()").value(80 - 14))
                .andExpect(jsonPath("$.sma50.length()").value(80 - 49));
        assertThat(twelveData.paths()).singleElement().satisfies(path -> assertThat(path)
                .contains("interval=5min", "outputsize=80"));
    }

    @Test
    @DisplayName("a steadily rising series reads as the overbought zone, a falling one as the oversold zone")
    void readingsFromRealSeries() throws Exception {
        double[] rising = new double[252];
        double[] falling = new double[252];
        for (int i = 0; i < 252; i++) {
            rising[i] = 100 + i;
            falling[i] = 500 - i;
        }
        twelveData.respondWith(200, daily(rising));
        indicators("UP", "1Y").andExpect(jsonPath("$.readings[?(@.indicator=='RSI (14)')].label")
                .value(org.hamcrest.Matchers.hasItem("RSI is 100.0, in the overbought zone")));

        twelveData.respondWith(200, daily(falling));
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        indicators("DOWN", "1Y").andExpect(jsonPath("$.readings[?(@.indicator=='RSI (14)')].label")
                .value(org.hamcrest.Matchers.hasItem("RSI is 0.0, in the oversold zone")));
    }

    @Test
    @DisplayName("nothing the endpoint returns contains advice")
    void neverAdvises() throws Exception {
        twelveData.respondWith(200, daily(walk(252)));
        String body = indicators("AAPL", "1Y").andReturn().getResponse().getContentAsString().toLowerCase();

        assertThat(Pattern.compile("\\b(buy|sell|bullish|bearish|recommend)\\b").matcher(body).find()).isFalse();
    }

    @Test
    @DisplayName("provider errors: unknown symbol 404, rate limit 503; a bad range or symbol is 400; no token 401")
    void errors() throws Exception {
        twelveData.respondWith(200, "{\"code\":404,\"message\":\"symbol not found\",\"status\":\"error\"}");
        indicators("NOPE", "1Y").andExpect(status().isNotFound());

        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        twelveData.respondWith(429, "{\"code\":429,\"message\":\"limit\",\"status\":\"error\"}");
        indicators("AAPL", "1Y").andExpect(status().isServiceUnavailable());

        indicators("AAPL", "10Y").andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/stocks/A A/indicators").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/stocks/AAPL/indicators")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/stocks/AAPL/fundamentals")).andExpect(status().isUnauthorized());
    }

    // ---- fundamentals ----

    @Test
    @DisplayName("fundamentals combine the profile and the metrics, with market cap in dollars and ratios to 2 places")
    void fundamentals() throws Exception {
        aapl();

        fundamentals("aapl").andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.name").value("Apple Inc"))
                .andExpect(jsonPath("$.exchange").value("NASDAQ NMS - GLOBAL MARKET"))
                .andExpect(jsonPath("$.industry").value("Technology"))
                .andExpect(jsonPath("$.marketCap").value("4961437500000"))
                .andExpect(jsonPath("$.peRatio").value("38.48"))
                .andExpect(jsonPath("$.eps").value("8.72"))
                .andExpect(jsonPath("$.roe").value("137.18"))
                .andExpect(jsonPath("$.dividendYield").value("0.51"))
                .andExpect(jsonPath("$.week52High").value("345.34"))
                .andExpect(jsonPath("$.week52Low").value("243.42"))
                .andExpect(jsonPath("$.beta").value("1.09"));
    }

    @Test
    @DisplayName("a ratio the provider does not report is absent, not zero")
    void missingRatiosAbsent() throws Exception {
        finnhub.respondForPath("/stock/profile2", "{\"name\":\"Loss Co\",\"exchange\":\"NYSE\",\"marketCapitalization\":100}");
        finnhub.respondForPath("/stock/metric", "{\"metric\":{\"52WeekHigh\":12.5,\"52WeekLow\":3.1}}");

        fundamentals("LOSS").andExpect(status().isOk())
                .andExpect(jsonPath("$.week52High").value("12.50"))
                .andExpect(jsonPath("$.peRatio").doesNotExist())
                .andExpect(jsonPath("$.eps").doesNotExist())
                .andExpect(jsonPath("$.dividendYield").doesNotExist())
                .andExpect(jsonPath("$.industry").doesNotExist());
    }

    @Test
    @DisplayName("fundamentals cost two upstream calls once, then none while cached")
    void fundamentalsCached() throws Exception {
        aapl();

        fundamentals("AAPL").andExpect(status().isOk());
        assertThat(finnhub.requestCount()).isEqualTo(2);
        fundamentals("AAPL").andExpect(status().isOk());
        fundamentals("aapl").andExpect(status().isOk());
        assertThat(finnhub.requestCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("a symbol the provider does not carry is 404")
    void fundamentalsUnknown() throws Exception {
        finnhub.respondForPath("/stock/profile2", "{}");
        finnhub.respondForPath("/stock/metric", "{\"metric\":{},\"series\":{}}");

        fundamentals("NOPE").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("an upstream failure is 503")
    void fundamentalsUpstreamFailure() throws Exception {
        finnhub.respondWith(500, "{}");
        fundamentals("AAPL").andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("the first look records the exchange and industry search never learns, without overwriting what is known")
    void fillsTheStockCatalogue() throws Exception {
        aapl();
        stockRepository.save(new Stock("AAPL", "Apple Inc", null)); // as search leaves it
        fundamentals("AAPL").andExpect(status().isOk());

        Stock stock = stockRepository.findById("AAPL").orElseThrow();
        assertThat(stock.getExchange()).isEqualTo("NASDAQ NMS - GLOBAL MARKET");
        assertThat(stock.getSector()).isEqualTo("Technology");

        // A symbol never searched gets a row of its own.
        finnhub.respondForPath("/stock/profile2", "{\"name\":\"New Co\",\"exchange\":\"NYSE\",\"finnhubIndustry\":\"Energy\",\"marketCapitalization\":5}");
        fundamentals("NEWCO").andExpect(status().isOk());
        assertThat(stockRepository.findById("NEWCO")).hasValueSatisfying(s -> {
            assertThat(s.getName()).isEqualTo("New Co");
            assertThat(s.getSector()).isEqualTo("Energy");
        });

        // Known values are left alone even if the provider now disagrees.
        Stock known = new Stock("KEEP", "Keep Co", "NASDAQ");
        known.setSector("Finance");
        stockRepository.save(known);
        finnhub.respondForPath("/stock/profile2", "{\"name\":\"Keep Co\",\"exchange\":\"OTHER\",\"finnhubIndustry\":\"Other\",\"marketCapitalization\":5}");
        fundamentals("KEEP").andExpect(status().isOk());
        assertThat(stockRepository.findById("KEEP").orElseThrow().getExchange()).isEqualTo("NASDAQ");
        assertThat(stockRepository.findById("KEEP").orElseThrow().getSector()).isEqualTo("Finance");
    }

    // ---- helpers ----

    private static final Instant START = Instant.parse("2025-01-01T00:00:00Z");

    private ResultActions indicators(String symbol, String range) throws Exception {
        return mockMvc.perform(get("/api/stocks/" + symbol + "/indicators?range=" + range)
                .header("Authorization", "Bearer " + token));
    }

    private ResultActions fundamentals(String symbol) throws Exception {
        return mockMvc.perform(get("/api/stocks/" + symbol + "/fundamentals").header("Authorization", "Bearer " + token));
    }

    private void aapl() {
        finnhub.respondForPath("/stock/profile2",
                "{\"name\":\"Apple Inc\",\"exchange\":\"NASDAQ NMS - GLOBAL MARKET\",\"finnhubIndustry\":\"Technology\",\"marketCapitalization\":4961437.5}");
        finnhub.respondForPath("/stock/metric",
                "{\"metric\":{\"peTTM\":38.4816,\"epsTTM\":8.723299999999998,\"roeTTM\":137.17999999999998,"
                        + "\"dividendYieldIndicatedAnnual\":0.50534,\"52WeekHigh\":345.34,\"52WeekLow\":243.42,\"beta\":1.0942847}}");
    }

    private static double[] walk(int n) {
        Random random = new Random(7);
        double[] values = new double[n];
        double price = 100;
        for (int i = 0; i < n; i++) {
            price += random.nextGaussian() * 1.5 + 0.02;
            values[i] = Math.round(price * 100) / 100.0;
        }
        return values;
    }

    /** Twelve Data sends newest first by default, so the fixtures do too. */
    private static String daily(double[] closes) {
        return series(closes, i -> START.plus(i, ChronoUnit.DAYS).toString().substring(0, 10));
    }

    private static String intraday(double[] closes) {
        return series(closes, i -> START.plus(i * 5L, ChronoUnit.MINUTES).toString().substring(0, 16).replace('T', ' ') + ":00");
    }

    private static String series(double[] closes, java.util.function.IntFunction<String> time) {
        StringBuilder values = new StringBuilder();
        for (int i = closes.length - 1; i >= 0; i--) {
            if (values.length() > 0) values.append(',');
            String c = String.format("%.2f", closes[i]);
            values.append("{\"datetime\":\"%s\",\"open\":\"%s\",\"high\":\"%s\",\"low\":\"%s\",\"close\":\"%s\",\"volume\":\"1000\"}"
                    .formatted(time.apply(i), c, c, c, c));
        }
        return "{\"meta\":{},\"status\":\"ok\",\"values\":[" + values + "]}";
    }

    private String signUp() throws Exception {
        String response = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"analyst@example.com\",\"password\":\"Password123\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return response.replaceAll("(?s).*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }
}
