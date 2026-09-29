package com.portfoliopro.portfolio;

import com.portfoliopro.auth.UserRepository;
import com.portfoliopro.market.Stock;
import com.portfoliopro.market.StockRepository;
import com.portfoliopro.market.StubFinnhub;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@DisplayName("Watchlist API")
class WatchlistApiTest {

    private static final int MAX = 25;

    private static StubFinnhub finnhub;

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RiskSettingsRepository riskSettingsRepository;
    @Autowired private WatchlistRepository watchlistRepository;
    @Autowired private StockRepository stockRepository;
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
        stockRepository.deleteAll();
        riskSettingsRepository.deleteAll();
        userRepository.deleteAll();
        token = signUp("watcher@example.com");
        userId = userRepository.findByEmail("watcher@example.com").orElseThrow().getId();
        price("AAPL", "100.00", "2.50", "2.56");
    }

    @Test
    @DisplayName("adding a symbol returns it priced, with the day's change, and it then shows in the list")
    void addAndList() throws Exception {
        stockRepository.save(new Stock("AAPL", "Apple Inc", null));

        add("aapl").andExpect(status().isCreated())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.name").value("Apple Inc"))
                .andExpect(jsonPath("$.price").value("100.00"))
                .andExpect(jsonPath("$.change").value("2.50"))
                .andExpect(jsonPath("$.percentChange").value("2.56"));

        list().andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].symbol").value("AAPL"));
    }

    @Test
    @DisplayName("following a symbol twice is a no-op: 200 the second time, one row")
    void addIsIdempotent() throws Exception {
        add("AAPL").andExpect(status().isCreated());
        add("aapl").andExpect(status().isOk());

        assertThat(watchlistRepository.countByUserId(userId)).isEqualTo(1);
    }

    @Test
    @DisplayName("the list keeps the order symbols were added in")
    void order() throws Exception {
        price("MSFT", "200.00", "1.00", "0.50");
        price("TSLA", "300.00", "-3.00", "-1.00");
        add("TSLA").andExpect(status().isCreated());
        Thread.sleep(5); // created_at has microsecond precision, but do not lean on it
        add("AAPL").andExpect(status().isCreated());
        Thread.sleep(5);
        add("MSFT").andExpect(status().isCreated());

        list().andExpect(jsonPath("$[0].symbol").value("TSLA"))
                .andExpect(jsonPath("$[1].symbol").value("AAPL"))
                .andExpect(jsonPath("$[2].symbol").value("MSFT"))
                .andExpect(jsonPath("$[0].change").value("-3.00"));
    }

    @Test
    @DisplayName("an unknown symbol is 404 and is not stored")
    void unknownSymbol() throws Exception {
        finnhub.respondForSymbol("NOPE", "{\"c\":0,\"d\":0,\"dp\":0,\"h\":0,\"l\":0,\"o\":0,\"pc\":0,\"t\":0}");

        add("NOPE").andExpect(status().isNotFound());
        assertThat(watchlistRepository.count()).isZero();
    }

    @Test
    @DisplayName("a watchlist is capped, and a symbol already on it can still be re-added at the cap")
    void cap() throws Exception {
        for (int i = 0; i < MAX; i++) {
            String symbol = "S" + i;
            price(symbol, "10.00", "0.10", "1.00");
            add(symbol).andExpect(status().isCreated());
        }
        price("EXTRA", "10.00", "0.10", "1.00");
        add("EXTRA").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("WATCHLIST_FULL"));
        add("S3").andExpect(status().isOk());

        assertThat(watchlistRepository.countByUserId(userId)).isEqualTo(MAX);
    }

    @Test
    @DisplayName("concurrent adds cannot push a watchlist past the cap")
    void concurrentAddsRespectTheCap() throws Exception {
        for (int i = 0; i < MAX - 5; i++) {
            watchlistRepository.save(new WatchlistEntry(userId, "P" + i));
        }
        int threads = 10;
        for (int i = 0; i < threads; i++) {
            price("N" + i, "10.00", "0.10", "1.00");
        }
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                String symbol = "N" + i;
                results.add(pool.submit(() -> {
                    go.await();
                    return add(symbol).andReturn().getResponse().getStatus();
                }));
            }
            go.countDown();
            int created = 0;
            for (Future<Integer> result : results) {
                if (result.get() == 201) created++;
            }
            assertThat(created).isEqualTo(5);
        } finally {
            pool.shutdownNow();
        }
        assertThat(watchlistRepository.countByUserId(userId)).isEqualTo(MAX);
    }

    @Test
    @DisplayName("removing a symbol works once, then it is 404")
    void remove() throws Exception {
        add("AAPL").andExpect(status().isCreated());

        mockMvc.perform(delete("/api/watchlist/aapl").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/watchlist/AAPL").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        list().andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("a quote that fails leaves that symbol unpriced instead of failing the list")
    void unpricedFallback() throws Exception {
        price("MSFT", "200.00", "1.00", "0.50");
        add("AAPL").andExpect(status().isCreated());
        add("MSFT").andExpect(status().isCreated());
        finnhub.respondForSymbol("AAPL", "not json");
        clearCaches();

        list().andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$[0].price").doesNotExist())
                .andExpect(jsonPath("$[1].price").value("200.00"));
    }

    @Test
    @DisplayName("the whole list costs one upstream call per symbol, and none while the quotes are cached")
    void quotesAreCachedAcrossTheList() throws Exception {
        for (String symbol : List.of("AAPL", "MSFT", "TSLA")) {
            price(symbol, "10.00", "0.10", "1.00");
            watchlistRepository.save(new WatchlistEntry(userId, symbol));
        }
        finnhub.resetCounts();

        list().andExpect(jsonPath("$.length()").value(3));
        assertThat(finnhub.requestCount()).isEqualTo(3);
        list().andExpect(jsonPath("$.length()").value(3));
        assertThat(finnhub.requestCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("one user's watchlist never shows in another's, and every route needs a token")
    void scopedToTheUser() throws Exception {
        add("AAPL").andExpect(status().isCreated());
        String other = signUp("other@example.com");

        mockMvc.perform(get("/api/watchlist").header("Authorization", "Bearer " + other))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(delete("/api/watchlist/AAPL").header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
        assertThat(watchlistRepository.countByUserId(userId)).isEqualTo(1);

        mockMvc.perform(get("/api/watchlist")).andExpect(status().isUnauthorized());
        mockMvc.perform(MockMvcRequestBuilders.post("/api/watchlist").contentType(MediaType.APPLICATION_JSON)
                .content("{\"symbol\":\"AAPL\"}")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/watchlist/AAPL")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a missing or non-ticker symbol is 400")
    void validation() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/watchlist").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        add("A A").andExpect(status().isBadRequest());
        add("").andExpect(status().isBadRequest());
        mockMvc.perform(delete("/api/watchlist/A A").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        assertThat(watchlistRepository.count()).isZero();
    }

    // ---- helpers ----

    private ResultActions add(String symbol) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post("/api/watchlist")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"symbol\":\"%s\"}".formatted(symbol)));
    }

    private ResultActions list() throws Exception {
        return mockMvc.perform(get("/api/watchlist").header("Authorization", "Bearer " + token));
    }

    private void price(String symbol, String value, String change, String percent) {
        finnhub.respondForSymbol(symbol,
                "{\"c\":%s,\"d\":%s,\"dp\":%s,\"h\":%s,\"l\":%s,\"o\":%s,\"pc\":%s,\"t\":1727539200}"
                        .formatted(value, change, percent, value, value, value, value));
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
