package com.portfoliopro.market;

import com.portfoliopro.common.exception.MarketDataUnavailableException;
import com.portfoliopro.common.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatusCode;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Chart history. Finnhub's candle endpoint is paid-only, so this comes from Twelve
 * Data, whose free plan is small (about 8 calls a minute and 800 a day). The cache
 * sits here, as it does on {@link FinnhubClient}, and {@code @Cacheable} does not
 * cache a thrown exception, so a failure is retried rather than remembered.
 */
@Component
public class TwelveDataClient {

    public static final String CANDLE_CACHE = "candles";

    private static final Logger log = LoggerFactory.getLogger(TwelveDataClient.class);
    private static final DateTimeFormatter INTRADAY = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();

    private final RestClient restClient;
    private final String apiKey;

    public TwelveDataClient(
            @Value("${app.twelvedata.base-url}") String baseUrl,
            @Value("${app.twelvedata.api-key:}") String apiKey,
            @Value("${app.twelvedata.timeout-seconds:8}") long timeoutSeconds) {
        this.apiKey = apiKey;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    @Cacheable(CANDLE_CACHE)
    public List<Candle> candles(String symbol, CandleRange range) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new MarketDataUnavailableException("Chart data is not configured");
        }
        JsonNode body = get(symbol, range);

        if ("error".equals(text(body, "status"))) {
            throw errorFor(symbol, body);
        }
        if (!body.path("values").isArray()) {
            // Neither an error body nor a series: a gateway page, say, not "no candles".
            throw new MarketDataUnavailableException("Chart data provider returned an unexpected response");
        }
        List<Candle> candles = new ArrayList<>();
        for (JsonNode node : body.path("values")) {
            Instant time = parseTime(text(node, "datetime"));
            BigDecimal open = decimal(node, "open");
            BigDecimal high = decimal(node, "high");
            BigDecimal low = decimal(node, "low");
            BigDecimal close = decimal(node, "close");
            if (time == null || open == null || high == null || low == null || close == null) {
                continue; // a malformed bar is dropped rather than drawn wrongly
            }
            BigDecimal volume = decimal(node, "volume");
            candles.add(new Candle(time, open, high, low, close, volume == null ? 0 : volume.longValue()));
        }
        // Asked for ascending, but the chart library rejects unordered data outright.
        candles.sort((a, b) -> a.time().compareTo(b.time()));
        return List.copyOf(candles);
    }

    private JsonNode get(String symbol, CandleRange range) {
        String raw;
        try {
            raw = restClient.get()
                    .uri(uri -> uri.path("/time_series")
                            .queryParam("symbol", symbol)
                            .queryParam("interval", range.interval())
                            .queryParam("outputsize", range.size())
                            .queryParam("order", "asc")
                            // UTC, so intraday bars need no exchange-timezone conversion.
                            .queryParam("timezone", "UTC")
                            .queryParam("apikey", apiKey)
                            .build())
                    .retrieve()
                    // Errors carry a JSON body worth reading, so do not throw on the status.
                    .onStatus(HttpStatusCode::isError, (request, response) -> { })
                    .body(String.class);
        } catch (RestClientException ex) {
            // The apikey query parameter must never reach a log or a client.
            log.warn("Twelve Data call failed: {}", ex.getMessage());
            throw new MarketDataUnavailableException("Chart data provider is not responding");
        }
        if (raw == null || raw.isBlank()) {
            throw new MarketDataUnavailableException("Chart data provider returned an empty response");
        }
        try {
            return MAPPER.readTree(raw);
        } catch (RuntimeException ex) {
            log.warn("Twelve Data response was not readable JSON", ex);
            throw new MarketDataUnavailableException("Chart data provider returned an unreadable response");
        }
    }

    /** 400 and 404 mean the symbol is not one it carries; anything else is the provider's problem. */
    private static RuntimeException errorFor(String symbol, JsonNode body) {
        int code = body.path("code").isNumber() ? body.path("code").intValue() : 0;
        if (code == 400 || code == 404) {
            return new NotFoundException("No chart data for symbol: " + symbol);
        }
        log.warn("Twelve Data returned error {}: {}", code, text(body, "message"));
        return new MarketDataUnavailableException(
                code == 429 ? "Chart data rate limit reached, try again shortly" : "Chart data provider refused the request");
    }

    /** Daily and weekly bars carry a date only; intraday bars carry date and time. Both are UTC. */
    private static Instant parseTime(String value) {
        if (value == null) {
            return null;
        }
        try {
            if (value.length() == 10) {
                return LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC);
            }
            return LocalDateTime.parse(value, INTRADAY).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    /** Twelve Data sends numbers as strings. */
    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        try {
            if (value.isString()) {
                return new BigDecimal(value.stringValue());
            }
            return value.isNumber() ? value.decimalValue() : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isString() ? value.stringValue() : null;
    }
}
