package com.portfoliopro.trading.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * How a portfolio has done over a range, rebuilt from its ledger and trades. Money is a
 * string throughout (rule 1); dates are UTC calendar days.
 *
 * <p>{@code estimatedSymbols} lists holdings that were valued at their trade price on
 * some day because no price history was available for them.
 */
public record PerformanceResponse(
        String range,
        LocalDate from,
        LocalDate to,
        List<Point> points,
        Summary summary,
        List<PositionPnl> positions,
        List<String> estimatedSymbols) {

    public record Point(LocalDate date, String value, String cash, String invested) {
    }

    /** {@code bestDay} and {@code worstDay} are absent unless something actually moved that way. */
    public record Summary(
            String startValue, String endValue, String change, String changePercent, DayMove bestDay, DayMove worstDay) {
    }

    public record DayMove(LocalDate date, String change, String changePercent) {
    }

    /** {@code unrealizedPnl} is absent for a closed position, or one that could not be priced. */
    public record PositionPnl(
            String symbol, boolean open, long quantity, String realizedPnl, String unrealizedPnl, String totalPnl) {
    }
}
