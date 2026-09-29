package com.portfoliopro.portfolio;

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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.io.IOException;
import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@DisplayName("Portfolio API")
class PortfolioApiTest {

    private static StubFinnhub finnhub;

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RiskSettingsRepository riskSettingsRepository;
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
        token = signUp("holder@example.com");
        userId = userRepository.findByEmail("holder@example.com").orElseThrow().getId();
        // Room to buy several positions without the default 20% cap or $5,000 limit interfering.
        RiskSettings settings = riskSettingsRepository.findByUserId(userId).orElseThrow();
        settings.setMaxPositionPct(new BigDecimal("100.00"));
        settings.setMaxOrderValue(new BigDecimal("1000000"));
        riskSettingsRepository.save(settings);
        price("AAPL", "100.00");
        price("MSFT", "200.00");
    }

    @Test
    @DisplayName("an empty portfolio is just cash")
    void empty() throws Exception {
        mockMvc.perform(get("/api/portfolio").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cash").value("100000.00"))
                .andExpect(jsonPath("$.totalValue").value("100000.00"))
                .andExpect(jsonPath("$.holdings.length()").value(0));
        mockMvc.perform(get("/api/portfolio/allocation").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].label").value("Cash"))
                .andExpect(jsonPath("$[0].percent").value("100.00"));
    }

    @Test
    @DisplayName("holdings are valued at the current price with unrealized P&L against average cost")
    void unrealized() throws Exception {
        order("AAPL", "BUY", 10); // cost 1,000
        price("AAPL", "110.00");

        portfolio()
                .andExpect(jsonPath("$.cash").value("99000.00"))
                .andExpect(jsonPath("$.holdingsValue").value("1100.00"))
                .andExpect(jsonPath("$.totalValue").value("100100.00"))
                .andExpect(jsonPath("$.unrealizedPnl").value("100.00"))
                .andExpect(jsonPath("$.holdings[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$.holdings[0].quantity").value(10))
                .andExpect(jsonPath("$.holdings[0].avgPrice").value("100.00"))
                .andExpect(jsonPath("$.holdings[0].price").value("110.00"))
                .andExpect(jsonPath("$.holdings[0].marketValue").value("1100.00"))
                .andExpect(jsonPath("$.holdings[0].unrealizedPnl").value("100.00"))
                .andExpect(jsonPath("$.holdings[0].unrealizedPnlPercent").value("10.00"));
    }

    @Test
    @DisplayName("a loss is negative and a fully sold position drops off the list but its realized P&L stays in the total")
    void realizedAndClosed() throws Exception {
        order("AAPL", "BUY", 10);
        price("AAPL", "90.00");
        order("AAPL", "SELL", 10); // realizes -100 and closes the position
        order("MSFT", "BUY", 5);   // cost 1,000
        price("MSFT", "180.00");

        portfolio()
                .andExpect(jsonPath("$.holdings.length()").value(1))
                .andExpect(jsonPath("$.holdings[0].symbol").value("MSFT"))
                .andExpect(jsonPath("$.holdings[0].unrealizedPnl").value("-100.00"))
                .andExpect(jsonPath("$.holdings[0].unrealizedPnlPercent").value("-10.00"))
                .andExpect(jsonPath("$.unrealizedPnl").value("-100.00"))
                .andExpect(jsonPath("$.realizedPnl").value("-100.00"));
    }

    @Test
    @DisplayName("allocation splits the portfolio across stocks and cash, largest first, summing to about 100%")
    void allocation() throws Exception {
        order("AAPL", "BUY", 100); // 10,000
        order("MSFT", "BUY", 100); // 20,000, so cash is 70,000 of 100,000

        mockMvc.perform(get("/api/portfolio/allocation").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].symbol").value("MSFT"))
                .andExpect(jsonPath("$[0].value").value("20000.00"))
                .andExpect(jsonPath("$[0].percent").value("20.00"))
                .andExpect(jsonPath("$[1].symbol").value("AAPL"))
                .andExpect(jsonPath("$[1].percent").value("10.00"))
                .andExpect(jsonPath("$[2].label").value("Cash"))
                .andExpect(jsonPath("$[2].symbol").doesNotExist())
                .andExpect(jsonPath("$[2].percent").value("70.00"));
    }

    @Test
    @DisplayName("if a quote is unavailable the holding is returned unpriced and valued at cost, not a failed request")
    void unpricedFallsBackToCost() throws Exception {
        order("AAPL", "BUY", 10);
        finnhub.respondWith(500, "{}");
        clearCaches();

        portfolio()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holdings[0].price").doesNotExist())
                .andExpect(jsonPath("$.holdings[0].unrealizedPnl").doesNotExist())
                .andExpect(jsonPath("$.holdings[0].marketValue").value("1000.00"))
                .andExpect(jsonPath("$.totalValue").value("100000.00"))
                .andExpect(jsonPath("$.unrealizedPnl").value("0.00"));
    }

    @Test
    @DisplayName("one user's holdings never appear in another's portfolio, and it needs a token")
    void scopedToTheUser() throws Exception {
        order("AAPL", "BUY", 10);
        String other = signUp("other@example.com");

        mockMvc.perform(get("/api/portfolio").header("Authorization", "Bearer " + other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holdings.length()").value(0))
                .andExpect(jsonPath("$.cash").value("100000.00"));
        mockMvc.perform(get("/api/portfolio")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/portfolio/allocation")).andExpect(status().isUnauthorized());
    }

    // ---- helpers ----

    private ResultActions portfolio() throws Exception {
        return mockMvc.perform(get("/api/portfolio").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private void order(String symbol, String side, long quantity) throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"%s\",\"side\":\"%s\",\"type\":\"MARKET\",\"quantity\":%d}"
                                .formatted(symbol, side, quantity)))
                .andExpect(status().isCreated());
    }

    /** Sets one symbol's price; the quote cache would otherwise keep serving the old one. */
    private void price(String symbol, String value) {
        finnhub.respondForSymbol(symbol,
                "{\"c\":%s,\"d\":0,\"dp\":0,\"h\":%s,\"l\":%s,\"o\":%s,\"pc\":%s,\"t\":1727539200}"
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
