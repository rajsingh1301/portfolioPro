package com.portfoliopro.market;

import com.portfoliopro.common.exception.MarketDataUnavailableException;
import com.portfoliopro.market.dto.StockSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The only thing in the app that talks to Finnhub.
 *
 * <p>Both calls are cached, and the cache sits here rather than in the service so that
 * a symbol nobody recognises still costs one upstream call per TTL instead of one per
 * request — the free tier allows about 60 calls a minute and that limit, not the
 * database, is what the app has to survive (ARCHITECTURE §7).
 */
@Component
public class FinnhubClient {

    public static final String QUOTE_CACHE = "quotes";
    public static final String SEARCH_CACHE = "symbolSearch";
    public static final String FUNDAMENTALS_CACHE = "fundamentals";

    private static final Logger log = LoggerFactory.getLogger(FinnhubClient.class);
    private static final int MAX_SEARCH_RESULTS = 10;

    /**
     * Reads floating-point JSON as BigDecimal. Without this Jackson would hand back a
     * double and `197.33` could arrive as `197.32999999999998`.
     */
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();

    private final RestClient restClient;
    private final String apiKey;

    public FinnhubClient(
            @Value("${app.finnhub.base-url}") String baseUrl,
            @Value("${app.finnhub.api-key}") String apiKey,
            @Value("${app.finnhub.timeout-seconds:5}") long timeoutSeconds) {
        this.apiKey = apiKey;
        // A slow provider must not hold a request thread open indefinitely.
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    @Cacheable(QUOTE_CACHE)
    public FinnhubQuote quote(String symbol) {
        JsonNode body = get("/quote", uri -> uri.queryParam("symbol", symbol));
        return new FinnhubQuote(
                decimal(body, "c"),
                decimal(body, "d"),
                decimal(body, "dp"),
                decimal(body, "h"),
                decimal(body, "l"),
                decimal(body, "o"),
                decimal(body, "pc"),
                epochSeconds(body, "t"));
    }

    /**
     * Two calls (profile and metrics), cached together for hours: ratios move with
     * quarterly filings, not tick by tick, so a symbol costs two calls per TTL.
     */
    @Cacheable(FUNDAMENTALS_CACHE)
    public Fundamentals fundamentals(String symbol) {
        JsonNode profile = get("/stock/profile2", uri -> uri.queryParam("symbol", symbol));
        JsonNode metrics = get("/stock/metric", uri -> uri.queryParam("symbol", symbol).queryParam("metric", "all"))
                .path("metric");
        BigDecimal marketCapMillions = decimal(profile, "marketCapitalization");
        if (marketCapMillions == null) {
            marketCapMillions = decimal(metrics, "marketCapitalization");
        }
        BigDecimal pe = decimal(metrics, "peTTM");
        return new Fundamentals(
                text(profile, "name"),
                text(profile, "exchange"),
                text(profile, "finnhubIndustry"),
                marketCapMillions == null ? null : marketCapMillions.multiply(BigDecimal.valueOf(1_000_000)),
                pe != null ? pe : decimal(metrics, "peBasicExclExtraTTM"),
                decimal(metrics, "epsTTM"),
                decimal(metrics, "roeTTM"),
                decimal(metrics, "dividendYieldIndicatedAnnual"),
                decimal(metrics, "52WeekHigh"),
                decimal(metrics, "52WeekLow"),
                decimal(metrics, "beta"));
    }

    @Cacheable(SEARCH_CACHE)
    public List<StockSearchResult> search(String query) {
        // `exchange=US` keeps the results to what the free tier can actually quote.
        JsonNode body = get("/search", uri -> uri.queryParam("q", query).queryParam("exchange", "US"));
        List<StockSearchResult> results = new ArrayList<>();
        for (JsonNode node : body.path("result")) {
            String symbol = text(node, "symbol");
            // Common Stock only: the free tier cannot quote options or warrants, and a
            // dotted symbol is a class or unit line we would not be able to trade.
            if (symbol == null || symbol.contains(".") || !"Common Stock".equals(text(node, "type"))) {
                continue;
            }
            String name = text(node, "description");
            results.add(new StockSearchResult(
                    symbol.toUpperCase(java.util.Locale.ROOT),
                    name == null ? symbol : name));
            if (results.size() == MAX_SEARCH_RESULTS) {
                break;
            }
        }
        return List.copyOf(results);
    }

    private JsonNode get(String path, java.util.function.UnaryOperator<org.springframework.web.util.UriBuilder> params) {
        String raw;
        try {
            raw = restClient.get()
                    .uri(uri -> params.apply(uri.path(path)).queryParam("token", apiKey).build())
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException ex) {
            // The key and the token query parameter must never reach a log or a client.
            log.warn("Finnhub call to {} failed: {}", path, ex.getMessage());
            throw new MarketDataUnavailableException("Market data provider is not responding");
        }
        if (raw == null || raw.isBlank()) {
            throw new MarketDataUnavailableException("Market data provider returned an empty response");
        }
        try {
            return MAPPER.readTree(raw);
        } catch (RuntimeException ex) {
            log.warn("Finnhub response from {} was not readable JSON", path, ex);
            throw new MarketDataUnavailableException("Market data provider returned an unreadable response");
        }
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.decimalValue() : null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isString() ? value.stringValue() : null;
    }

    private static Instant epochSeconds(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() && value.longValue() > 0 ? Instant.ofEpochSecond(value.longValue()) : null;
    }
}
