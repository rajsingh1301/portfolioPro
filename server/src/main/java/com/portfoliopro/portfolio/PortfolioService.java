package com.portfoliopro.portfolio;

import com.portfoliopro.auth.UserRepository;
import com.portfoliopro.common.exception.ApiException;
import com.portfoliopro.common.exception.NotFoundException;
import com.portfoliopro.market.MarketService;
import com.portfoliopro.portfolio.dto.AllocationSlice;
import com.portfoliopro.portfolio.dto.HoldingResponse;
import com.portfoliopro.portfolio.dto.PortfolioResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class PortfolioService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final UserRepository userRepository;
    private final HoldingRepository holdingRepository;
    private final MarketService marketService;
    private final TransactionTemplate readOnly;

    public PortfolioService(
            UserRepository userRepository,
            HoldingRepository holdingRepository,
            MarketService marketService,
            PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.holdingRepository = holdingRepository;
        this.marketService = marketService;
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    /** Cash and holdings as of one moment; prices are then applied outside the transaction. */
    private record Snapshot(BigDecimal cash, List<Holding> holdings) {
    }

    /** A holding with its (possibly missing) price already applied. */
    private record Valued(Holding holding, BigDecimal price, BigDecimal marketValue) {
    }

    public PortfolioResponse portfolio(Long userId) {
        Snapshot snapshot = snapshot(userId);
        List<Valued> open = value(snapshot.holdings());

        BigDecimal holdingsValue = open.stream().map(Valued::marketValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal unrealized = BigDecimal.ZERO;
        List<HoldingResponse> rows = new ArrayList<>();
        for (Valued valued : open) {
            Holding holding = valued.holding();
            String pnl = null;
            String pnlPercent = null;
            if (valued.price() != null) {
                BigDecimal cost = cost(holding);
                BigDecimal gain = valued.marketValue().subtract(cost);
                unrealized = unrealized.add(gain);
                pnl = money(gain);
                pnlPercent = percent(gain, cost);
            }
            rows.add(new HoldingResponse(
                    holding.getSymbol(),
                    holding.getQuantity(),
                    money(holding.getAvgPrice()),
                    valued.price() == null ? null : money(valued.price()),
                    money(valued.marketValue()),
                    pnl,
                    pnlPercent,
                    money(holding.getRealizedPnl())));
        }

        // Closed positions are not listed, but the profit or loss they booked still counts.
        BigDecimal realized = snapshot.holdings().stream()
                .map(Holding::getRealizedPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new PortfolioResponse(
                money(snapshot.cash()),
                money(holdingsValue),
                money(snapshot.cash().add(holdingsValue)),
                money(unrealized),
                money(realized),
                rows);
    }

    public List<AllocationSlice> allocation(Long userId) {
        Snapshot snapshot = snapshot(userId);
        List<Valued> open = value(snapshot.holdings());
        BigDecimal total = open.stream().map(Valued::marketValue).reduce(snapshot.cash(), BigDecimal::add);

        List<AllocationSlice> slices = new ArrayList<>();
        open.stream()
                .sorted(Comparator.comparing(Valued::marketValue).reversed())
                .forEach(valued -> slices.add(new AllocationSlice(
                        valued.holding().getSymbol(),
                        valued.holding().getSymbol(),
                        money(valued.marketValue()),
                        percent(valued.marketValue(), total))));
        slices.add(new AllocationSlice(null, "Cash", money(snapshot.cash()), percent(snapshot.cash(), total)));
        return slices;
    }

    private Snapshot snapshot(Long userId) {
        return readOnly.execute(status -> {
            BigDecimal cash = userRepository.findById(userId)
                    .orElseThrow(() -> new NotFoundException("User not found"))
                    .getCashBalance();
            return new Snapshot(cash, holdingRepository.findByUserId(userId));
        });
    }

    /**
     * Prices every open position from the quote cache. A symbol whose quote fails is
     * valued at cost rather than failing the whole page, since one unreachable quote
     * should not hide a portfolio the user can otherwise see.
     */
    private List<Valued> value(List<Holding> holdings) {
        List<Valued> valued = new ArrayList<>();
        for (Holding holding : holdings) {
            if (holding.getQuantity() == 0) {
                continue;
            }
            BigDecimal price = null;
            try {
                price = marketService.currentPrice(holding.getSymbol());
            } catch (ApiException ex) {
                // Left null: reported to the client as an unpriced holding.
            }
            BigDecimal marketValue = price == null ? cost(holding) : value(price, holding.getQuantity());
            valued.add(new Valued(holding, price, marketValue));
        }
        return valued;
    }

    private static BigDecimal cost(Holding holding) {
        return value(holding.getAvgPrice(), holding.getQuantity());
    }

    private static BigDecimal value(BigDecimal price, long quantity) {
        return price.multiply(BigDecimal.valueOf(quantity)).setScale(4, RoundingMode.HALF_UP);
    }

    /** {@code part} as a percentage of {@code whole}, to two places; zero when there is no whole. */
    private static String percent(BigDecimal part, BigDecimal whole) {
        if (whole.signum() == 0) {
            return "0.00";
        }
        return part.multiply(HUNDRED).divide(whole, 2, RoundingMode.HALF_UP).toPlainString();
    }

    /** Fixed to two decimals so the client never has to round money. */
    private static String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
