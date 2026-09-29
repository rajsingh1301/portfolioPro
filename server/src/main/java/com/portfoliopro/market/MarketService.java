package com.portfoliopro.market;

import com.portfoliopro.common.exception.NotFoundException;
import com.portfoliopro.market.dto.QuoteResponse;
import com.portfoliopro.market.dto.StockSearchResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class MarketService {

    /** Prices are shown to the cent; percentages to two places as well. */
    private static final int SCALE = 2;

    private final FinnhubClient finnhubClient;
    private final StockRepository stockRepository;

    public MarketService(FinnhubClient finnhubClient, StockRepository stockRepository) {
        this.finnhubClient = finnhubClient;
        this.stockRepository = stockRepository;
    }

    /**
     * Searches upstream and records any symbol it has not seen before, so that by the
     * time a user can place an order on a symbol the app already has its name. Only
     * the handful of results actually shown are stored.
     */
    @Transactional
    public List<StockSearchResult> search(String query) {
        List<StockSearchResult> results = finnhubClient.search(query.trim());
        if (!results.isEmpty()) {
            remember(results);
        }
        return results;
    }

    @Transactional(readOnly = true)
    public QuoteResponse quote(String symbol) {
        String normalized = normalizeSymbol(symbol);
        FinnhubQuote quote = finnhubClient.quote(normalized);
        if (quote.isEmpty()) {
            // Finnhub returns zeros rather than a 404 for a symbol it does not carry.
            throw new NotFoundException("Unknown symbol: " + normalized);
        }
        return new QuoteResponse(
                normalized,
                money(quote.current()),
                money(quote.change()),
                money(quote.percentChange()),
                money(quote.high()),
                money(quote.low()),
                money(quote.open()),
                money(quote.previousClose()),
                quote.timestamp());
    }

    private void remember(List<StockSearchResult> results) {
        Set<String> known = new HashSet<>();
        stockRepository.findBySymbolIn(results.stream().map(StockSearchResult::symbol).toList())
                .forEach(stock -> known.add(stock.getSymbol()));

        List<Stock> fresh = results.stream()
                .filter(result -> !known.contains(result.symbol()))
                .map(result -> new Stock(result.symbol(), result.name(), null))
                .toList();
        if (!fresh.isEmpty()) {
            stockRepository.saveAll(fresh);
        }
    }

    /** Null-safe, and fixed to two decimals so the client never has to round money. */
    private static String money(BigDecimal value) {
        return value == null ? null : value.setScale(SCALE, RoundingMode.HALF_UP).toPlainString();
    }

    private static String normalizeSymbol(String symbol) {
        return symbol.trim().toUpperCase(Locale.ROOT);
    }
}
