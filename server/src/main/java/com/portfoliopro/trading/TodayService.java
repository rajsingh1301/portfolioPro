package com.portfoliopro.trading;

import com.portfoliopro.common.OrderSide;
import com.portfoliopro.common.exception.ApiException;
import com.portfoliopro.market.FinnhubQuote;
import com.portfoliopro.market.MarketService;
import com.portfoliopro.portfolio.Holding;
import com.portfoliopro.portfolio.HoldingRepository;
import com.portfoliopro.portfolio.PortfolioService;
import com.portfoliopro.trading.TodayCalculator.Fill;
import com.portfoliopro.trading.dto.TodayResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Day P&amp;L, per position and in all. Lives in {@code trading} because it needs today's fills,
 * and nothing may depend on trading. See {@link TodayCalculator} for why it is not simply
 * quantity times the change from the previous close.
 *
 * <p>"Today" is the trading session the quote belongs to: it begins at midnight UTC on the day
 * the quote was struck. Trades since then are today's. That keeps the answer right when the
 * market is closed and the quote is yesterday's close: an order placed after the close, at that
 * close, has made nothing yet.
 */
@Service
public class TodayService {

    /** A symbol only matters here if it is held or was traded lately enough to belong to this session. */
    private static final Duration LOOKBACK = Duration.ofDays(4);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final TradeRepository tradeRepository;
    private final HoldingRepository holdingRepository;
    private final PortfolioService portfolioService;
    private final MarketService marketService;
    private final TransactionTemplate readOnly;
    private final ExecutorService quoteExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public TodayService(
            TradeRepository tradeRepository,
            HoldingRepository holdingRepository,
            PortfolioService portfolioService,
            MarketService marketService,
            PlatformTransactionManager transactionManager) {
        this.tradeRepository = tradeRepository;
        this.holdingRepository = holdingRepository;
        this.portfolioService = portfolioService;
        this.marketService = marketService;
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    private record Records(List<Holding> holdings, List<Trade> recentTrades) {
    }

    public TodayResponse today(Long userId) {
        Instant now = Instant.now();
        Records records = readOnly.execute(status -> new Records(
                holdingRepository.findByUserId(userId),
                tradeRepository.findByUserIdOrderByIdAsc(userId).stream()
                        .filter(trade -> trade.getExecutedAt().isAfter(now.minus(LOOKBACK)))
                        .toList()));

        Map<String, Long> held = new HashMap<>();
        records.holdings().forEach(holding -> held.put(holding.getSymbol(), holding.getQuantity()));
        Set<String> symbols = new TreeSet<>();
        records.holdings().stream().filter(h -> h.getQuantity() > 0).forEach(h -> symbols.add(h.getSymbol()));
        records.recentTrades().forEach(trade -> symbols.add(trade.getSymbol()));

        Map<String, CompletableFuture<Optional<FinnhubQuote>>> quotes = new HashMap<>();
        for (String symbol : symbols) {
            quotes.put(symbol, CompletableFuture.supplyAsync(() -> quote(symbol), quoteExecutor));
        }

        List<TodayResponse.Position> positions = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (String symbol : symbols) {
            long quantityNow = held.getOrDefault(symbol, 0L);
            Optional<FinnhubQuote> quote = quotes.get(symbol).join();
            if (quote.isEmpty()) {
                positions.add(new TodayResponse.Position(symbol, quantityNow, null, null, null, null));
                continue;
            }
            FinnhubQuote q = quote.get();
            Instant sessionStart = sessionStart(q, now);
            List<Fill> fills = records.recentTrades().stream()
                    .filter(trade -> trade.getSymbol().equals(symbol) && !trade.getExecutedAt().isBefore(sessionStart))
                    .map(trade -> new Fill(
                            trade.getSide() == OrderSide.BUY ? trade.getQuantity() : -trade.getQuantity(), trade.getPrice()))
                    .toList();
            // A closed position with nothing traded this session has nothing to say.
            if (quantityNow == 0 && fills.isEmpty()) {
                continue;
            }
            TodayCalculator.Result result = TodayCalculator.compute(
                    new TodayCalculator.Position(symbol, quantityNow, q.current(), q.previousClose(), fills));
            total = total.add(result.dayPnl());
            positions.add(new TodayResponse.Position(
                    symbol,
                    quantityNow,
                    money(q.previousClose()),
                    money(result.dayChange()),
                    money(result.dayChangePercent()),
                    money(result.dayPnl())));
        }

        // Cash moves only by trading, so the portfolio was worth "now minus today's gain" at the close.
        BigDecimal valueNow = new BigDecimal(portfolioService.portfolio(userId).totalValue());
        return new TodayResponse(money(total), percent(total, valueNow.subtract(total)), positions, now);
    }

    private Optional<FinnhubQuote> quote(String symbol) {
        try {
            return Optional.of(marketService.currentQuote(symbol));
        } catch (ApiException ex) {
            return Optional.empty(); // left unpriced, not failed
        }
    }

    /** Midnight UTC of the day the quote was struck; if it carries no time, of today. */
    private static Instant sessionStart(FinnhubQuote quote, Instant now) {
        Instant struck = quote.timestamp() != null ? quote.timestamp() : now;
        return LocalDate.ofInstant(struck, ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private static String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String percent(BigDecimal part, BigDecimal whole) {
        return whole.signum() == 0 ? "0.00" : part.multiply(HUNDRED).divide(whole, 2, RoundingMode.HALF_UP).toPlainString();
    }
}
