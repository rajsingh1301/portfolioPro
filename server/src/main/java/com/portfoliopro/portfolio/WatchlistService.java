package com.portfoliopro.portfolio;

import com.portfoliopro.auth.UserRepository;
import com.portfoliopro.common.exception.ApiException;
import com.portfoliopro.common.exception.NotFoundException;
import com.portfoliopro.common.exception.WatchlistFullException;
import com.portfoliopro.market.MarketService;
import com.portfoliopro.market.Stock;
import com.portfoliopro.market.StockRepository;
import com.portfoliopro.market.dto.QuoteResponse;
import com.portfoliopro.portfolio.dto.WatchlistItem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class WatchlistService {

    /** {@code created} is false when the symbol was already followed. */
    public record AddResult(WatchlistItem item, boolean created) {
    }

    private final UserRepository userRepository;
    private final WatchlistRepository watchlistRepository;
    private final StockRepository stockRepository;
    private final MarketService marketService;
    private final TransactionTemplate transaction;
    private final int maxSize;

    /**
     * A cold cache means one Finnhub call per symbol, each up to the client timeout. Run
     * one after another, a full list would take far too long to load, so they go out
     * together. Virtual threads make that cheap: the work is all waiting on the network.
     */
    private final ExecutorService quoteExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public WatchlistService(
            UserRepository userRepository,
            WatchlistRepository watchlistRepository,
            StockRepository stockRepository,
            MarketService marketService,
            PlatformTransactionManager transactionManager,
            @Value("${app.watchlist.max-size:25}") int maxSize) {
        this.userRepository = userRepository;
        this.watchlistRepository = watchlistRepository;
        this.stockRepository = stockRepository;
        this.marketService = marketService;
        this.transaction = new TransactionTemplate(transactionManager);
        this.maxSize = maxSize;
    }

    public List<WatchlistItem> list(Long userId) {
        List<String> symbols = transaction.execute(status -> watchlistRepository
                .findByUserIdOrderByCreatedAtAscSymbolAsc(userId).stream()
                .map(WatchlistEntry::getSymbol)
                .toList());
        return items(symbols);
    }

    /**
     * Adding is idempotent: following a symbol twice changes nothing. The user row is
     * locked first so the size check and the insert cannot be split by a concurrent add
     * (two adds at 24 symbols must not both get in).
     */
    public AddResult add(Long userId, String rawSymbol) {
        String symbol = MarketService.normalizeSymbol(rawSymbol);
        // Refuses a symbol that does not exist, and is a cached call, made before the lock.
        marketService.currentPrice(symbol);

        boolean created = Boolean.TRUE.equals(transaction.execute(status -> {
            userRepository.findByIdForUpdate(userId).orElseThrow(() -> new NotFoundException("User not found"));
            if (watchlistRepository.existsByUserIdAndSymbol(userId, symbol)) {
                return false;
            }
            if (watchlistRepository.countByUserId(userId) >= maxSize) {
                throw new WatchlistFullException(maxSize);
            }
            watchlistRepository.save(new WatchlistEntry(userId, symbol));
            return true;
        }));
        return new AddResult(items(List.of(symbol)).get(0), created);
    }

    public void remove(Long userId, String rawSymbol) {
        String symbol = MarketService.normalizeSymbol(rawSymbol);
        long removed = transaction.execute(status -> watchlistRepository.deleteByUserIdAndSymbol(userId, symbol));
        if (removed == 0) {
            throw new NotFoundException("Not on your watchlist: " + symbol);
        }
    }

    /** Names come from the local catalogue; prices from the quote cache, fetched in parallel. */
    private List<WatchlistItem> items(List<String> symbols) {
        if (symbols.isEmpty()) {
            return List.of();
        }
        Map<String, String> names = new HashMap<>();
        transaction.executeWithoutResult(status -> stockRepository.findBySymbolIn(symbols)
                .forEach((Stock stock) -> names.put(stock.getSymbol(), stock.getName())));

        List<CompletableFuture<WatchlistItem>> pending = symbols.stream()
                .map(symbol -> CompletableFuture.supplyAsync(() -> item(symbol, names.get(symbol)), quoteExecutor))
                .toList();
        return pending.stream().map(CompletableFuture::join).toList();
    }

    private WatchlistItem item(String symbol, String name) {
        try {
            QuoteResponse quote = marketService.quote(symbol);
            return new WatchlistItem(symbol, name, quote.price(), quote.change(), quote.percentChange());
        } catch (ApiException ex) {
            // Unpriced, not failed: one unreachable quote must not hide the whole list.
            return new WatchlistItem(symbol, name, null, null, null);
        }
    }
}
