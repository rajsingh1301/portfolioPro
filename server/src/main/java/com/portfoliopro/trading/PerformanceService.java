package com.portfoliopro.trading;

import com.portfoliopro.auth.UserRepository;
import com.portfoliopro.common.OrderSide;
import com.portfoliopro.common.exception.ApiException;
import com.portfoliopro.common.exception.NotFoundException;
import com.portfoliopro.market.Candle;
import com.portfoliopro.market.CandleRange;
import com.portfoliopro.market.MarketService;
import com.portfoliopro.portfolio.CashTransaction;
import com.portfoliopro.portfolio.CashTransactionRepository;
import com.portfoliopro.portfolio.Holding;
import com.portfoliopro.portfolio.HoldingRepository;
import com.portfoliopro.portfolio.PortfolioService;
import com.portfoliopro.portfolio.dto.HoldingResponse;
import com.portfoliopro.portfolio.dto.PortfolioResponse;
import com.portfoliopro.trading.PerformanceCalculator.Fill;
import com.portfoliopro.trading.PerformanceCalculator.LedgerEntry;
import com.portfoliopro.trading.dto.PerformanceResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Lives in {@code trading} because it is built from trades, and nothing may depend on
 * trading. It reads the ledger and holdings from {@code portfolio} and prices from
 * {@code market}, both of which trading may call.
 */
@Service
public class PerformanceService {

    /**
     * History costs one Twelve Data call per symbol (about 8 a minute on the free plan), so
     * only the most-traded symbols get real price history. The rest are valued at their trade
     * prices, and reported as estimated, rather than spending the quota on a long tail.
     */
    static final int MAX_HISTORY_SYMBOLS = 8;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final UserRepository userRepository;
    private final CashTransactionRepository ledgerRepository;
    private final TradeRepository tradeRepository;
    private final HoldingRepository holdingRepository;
    private final PortfolioService portfolioService;
    private final MarketService marketService;
    private final TransactionTemplate readOnly;
    private final ExecutorService historyExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public PerformanceService(
            UserRepository userRepository,
            CashTransactionRepository ledgerRepository,
            TradeRepository tradeRepository,
            HoldingRepository holdingRepository,
            PortfolioService portfolioService,
            MarketService marketService,
            PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.ledgerRepository = ledgerRepository;
        this.tradeRepository = tradeRepository;
        this.holdingRepository = holdingRepository;
        this.portfolioService = portfolioService;
        this.marketService = marketService;
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    /** The user's records as of one moment, read together. */
    private record History(LocalDate accountStart, List<CashTransaction> ledger, List<Trade> trades, List<Holding> holdings) {
    }

    public PerformanceResponse performance(Long userId, String rangeLabel) {
        PerformanceRange range = PerformanceRange.fromLabel(rangeLabel)
                .orElseThrow(() -> new NotFoundException("Unknown range: " + rangeLabel));
        History history = readOnly.execute(status -> new History(
                date(userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found")).getCreatedAt()),
                ledgerRepository.findByUserIdOrderByIdAsc(userId),
                tradeRepository.findByUserIdOrderByIdAsc(userId),
                holdingRepository.findByUserId(userId)));

        LocalDate to = LocalDate.now(ZoneOffset.UTC);
        LocalDate from = to.minusDays(range.days());
        if (from.isBefore(history.accountStart())) {
            from = history.accountStart(); // no history before the account existed
        }

        List<LedgerEntry> ledger = history.ledger().stream()
                .map(row -> new LedgerEntry(date(row.getCreatedAt()), row.getBalanceAfter()))
                .toList();
        List<Fill> fills = history.trades().stream()
                .map(trade -> new Fill(
                        date(trade.getExecutedAt()),
                        trade.getSymbol(),
                        trade.getSide() == OrderSide.BUY ? trade.getQuantity() : -trade.getQuantity(),
                        trade.getPrice()))
                .toList();

        PerformanceCalculator.Result result =
                PerformanceCalculator.compute(from, to, ledger, fills, closesFor(history.trades()));

        // Today is valued live, so the last point is exactly the figure at the top of the page.
        PortfolioResponse live = portfolioService.portfolio(userId);
        List<PerformanceResponse.Point> points = new ArrayList<>();
        for (PerformanceCalculator.Point point : result.points()) {
            boolean today = point.date().equals(to);
            points.add(new PerformanceResponse.Point(
                    point.date(),
                    today ? live.totalValue() : money(point.value()),
                    today ? live.cash() : money(point.cash()),
                    today ? live.holdingsValue() : money(point.invested())));
        }

        return new PerformanceResponse(
                range.label(),
                from,
                to,
                points,
                summary(points),
                positions(live, history.holdings()),
                result.estimated().stream().sorted().toList());
    }

    /** Daily closes for the most-traded symbols, fetched together; a symbol that fails has none. */
    private Map<String, NavigableMap<LocalDate, BigDecimal>> closesFor(List<Trade> trades) {
        List<String> symbols = trades.stream()
                .collect(Collectors.groupingBy(Trade::getSymbol, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(MAX_HISTORY_SYMBOLS)
                .map(Map.Entry::getKey)
                .toList();

        Map<String, CompletableFuture<NavigableMap<LocalDate, BigDecimal>>> pending = new HashMap<>();
        for (String symbol : symbols) {
            pending.put(symbol, CompletableFuture.supplyAsync(() -> series(symbol), historyExecutor));
        }
        Map<String, NavigableMap<LocalDate, BigDecimal>> closes = new HashMap<>();
        pending.forEach((symbol, future) -> {
            NavigableMap<LocalDate, BigDecimal> series = future.join();
            if (series != null) {
                closes.put(symbol, series);
            }
        });
        return closes;
    }

    /** The same year of daily candles the chart caches, so this usually costs no provider call. */
    private NavigableMap<LocalDate, BigDecimal> series(String symbol) {
        try {
            NavigableMap<LocalDate, BigDecimal> closes = new TreeMap<>();
            for (Candle candle : marketService.candleSeries(symbol, CandleRange.YEAR)) {
                closes.put(date(candle.time()), candle.close());
            }
            return closes.isEmpty() ? null : closes;
        } catch (ApiException ex) {
            return null; // no key, rate limited, or unknown: the calculator falls back to trade prices
        }
    }

    private PerformanceResponse.Summary summary(List<PerformanceResponse.Point> points) {
        BigDecimal start = new BigDecimal(points.get(0).value());
        BigDecimal end = new BigDecimal(points.get(points.size() - 1).value());

        PerformanceResponse.DayMove best = null;
        PerformanceResponse.DayMove worst = null;
        BigDecimal bestChange = BigDecimal.ZERO;
        BigDecimal worstChange = BigDecimal.ZERO;
        for (int i = 1; i < points.size(); i++) {
            BigDecimal before = new BigDecimal(points.get(i - 1).value());
            BigDecimal change = new BigDecimal(points.get(i).value()).subtract(before);
            if (change.compareTo(bestChange) > 0) {
                bestChange = change;
                best = new PerformanceResponse.DayMove(points.get(i).date(), money(change), percent(change, before));
            }
            if (change.compareTo(worstChange) < 0) {
                worstChange = change;
                worst = new PerformanceResponse.DayMove(points.get(i).date(), money(change), percent(change, before));
            }
        }
        BigDecimal change = end.subtract(start);
        return new PerformanceResponse.Summary(
                money(start), money(end), money(change), percent(change, start), best, worst);
    }

    /** Best contributor first. Closed positions count: their realized result is still part of the story. */
    private List<PerformanceResponse.PositionPnl> positions(PortfolioResponse live, List<Holding> holdings) {
        List<PerformanceResponse.PositionPnl> positions = new ArrayList<>();
        for (HoldingResponse open : live.holdings()) {
            BigDecimal realized = new BigDecimal(open.realizedPnl());
            BigDecimal unrealized = open.unrealizedPnl() == null ? null : new BigDecimal(open.unrealizedPnl());
            positions.add(new PerformanceResponse.PositionPnl(
                    open.symbol(),
                    true,
                    open.quantity(),
                    money(realized),
                    unrealized == null ? null : money(unrealized),
                    money(unrealized == null ? realized : realized.add(unrealized))));
        }
        for (Holding closed : holdings) {
            if (closed.getQuantity() == 0 && closed.getRealizedPnl().signum() != 0) {
                positions.add(new PerformanceResponse.PositionPnl(
                        closed.getSymbol(), false, 0, money(closed.getRealizedPnl()), null, money(closed.getRealizedPnl())));
            }
        }
        positions.sort(Comparator.comparing((PerformanceResponse.PositionPnl p) -> new BigDecimal(p.totalPnl())).reversed());
        return positions;
    }

    private static LocalDate date(Instant instant) {
        return LocalDate.ofInstant(instant, ZoneOffset.UTC);
    }

    private static String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String percent(BigDecimal part, BigDecimal whole) {
        return whole.signum() == 0 ? "0.00" : part.multiply(HUNDRED).divide(whole, 2, RoundingMode.HALF_UP).toPlainString();
    }
}
