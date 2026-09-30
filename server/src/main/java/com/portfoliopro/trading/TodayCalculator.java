package com.portfoliopro.trading;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * What one position made or lost since the previous close. Pure, so every case is a sum that
 * can be done by hand.
 *
 * <p>The naive answer, {@code quantity x (price - previous close)}, is wrong for shares bought
 * today: they were never held at the previous close, so they did not move from it. The right
 * answer is the change in the position's worth, less the cash that went in or out on the way:
 *
 * <pre>day P&amp;L = quantity now x price - quantity at the previous close x previous close
 *          - sum over today's fills of (signed quantity x fill price)</pre>
 *
 * <p>For shares held since before, that reduces to {@code quantity x (price - previous close)};
 * for shares bought today at {@code f}, to {@code quantity x (price - f)}; for a position opened
 * and closed today, to what the round trip made.
 */
public final class TodayCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private TodayCalculator() {
    }

    /** A fill since the session began: buys are positive, sells negative. */
    public record Fill(long signedQuantity, BigDecimal price) {
    }

    public record Position(
            String symbol, long quantityNow, BigDecimal price, BigDecimal previousClose, List<Fill> fillsToday) {
    }

    /** {@code dayChange} is per share, against the previous close. */
    public record Result(String symbol, long quantity, BigDecimal dayChange, BigDecimal dayChangePercent, BigDecimal dayPnl) {
    }

    public static Result compute(Position position) {
        long soldOrBought = position.fillsToday().stream().mapToLong(Fill::signedQuantity).sum();
        long quantityAtPreviousClose = position.quantityNow() - soldOrBought;

        BigDecimal cashPutIn = BigDecimal.ZERO; // net spent on today's fills
        for (Fill fill : position.fillsToday()) {
            cashPutIn = cashPutIn.add(fill.price().multiply(BigDecimal.valueOf(fill.signedQuantity())));
        }
        BigDecimal worthNow = position.price().multiply(BigDecimal.valueOf(position.quantityNow()));
        BigDecimal worthThen = position.previousClose().multiply(BigDecimal.valueOf(quantityAtPreviousClose));
        BigDecimal dayPnl = worthNow.subtract(worthThen).subtract(cashPutIn).setScale(4, RoundingMode.HALF_UP);

        BigDecimal dayChange = position.price().subtract(position.previousClose());
        BigDecimal percent = position.previousClose().signum() == 0
                ? BigDecimal.ZERO
                : dayChange.multiply(HUNDRED).divide(position.previousClose(), 4, RoundingMode.HALF_UP);
        return new Result(position.symbol(), position.quantityNow(), dayChange, percent, dayPnl);
    }
}
