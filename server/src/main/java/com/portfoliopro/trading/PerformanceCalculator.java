package com.portfoliopro.trading;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeSet;

/**
 * Rebuilds what a portfolio was worth on each day from the record of what happened to it:
 * the cash ledger says how much cash there was, the trades say what was held, and daily
 * closes say what it was worth. Pure, so it can be checked against sums done by hand.
 *
 * <p>Days: the ends of the window, every day something happened, and every day a price is
 * known, so weekends and holidays are simply absent for a symbol with real history. A symbol
 * with none gets every calendar day (see below).
 *
 * <p>Nothing is stored per day. History is always re-derived from the ledger and the trades,
 * which are the source of truth, so it can never disagree with them.
 */
public final class PerformanceCalculator {

    private PerformanceCalculator() {
    }

    /** The cash balance after a ledger row, dated (UTC) on the day it happened. Ascending. */
    public record LedgerEntry(LocalDate date, BigDecimal balanceAfter) {
    }

    /** A trade. Buys are positive quantity, sells negative. Ascending. */
    public record Fill(LocalDate date, String symbol, long signedQuantity, BigDecimal price) {
    }

    public record Point(LocalDate date, BigDecimal cash, BigDecimal invested, BigDecimal value) {
    }

    /** {@code estimated} names symbols that had to be valued at their trade price for some day. */
    public record Result(List<Point> points, Set<String> estimated) {
    }

    /**
     * One point per day something could have changed: the ends of the window, every day a
     * trade or a ledger row happened, and every day a price is known. Weekends and holidays
     * therefore carry the last close forward rather than appearing as gaps.
     *
     * @param closes daily closes by symbol. A symbol with none, or none yet on a given day,
     *               is valued at the price it last traded at, and reported in {@code estimated}
     */
    public static Result compute(
            LocalDate from,
            LocalDate to,
            List<LedgerEntry> ledger,
            List<Fill> fills,
            Map<String, NavigableMap<LocalDate, BigDecimal>> closes) {

        TreeSet<LocalDate> days = new TreeSet<>();
        days.add(from);
        days.add(to);
        ledger.forEach(entry -> addIfInWindow(days, entry.date(), from, to));
        fills.forEach(fill -> addIfInWindow(days, fill.date(), from, to));
        closes.values().forEach(series -> series.keySet().forEach(day -> addIfInWindow(days, day, from, to)));
        // A symbol with no price history gives no trading days to hang points on. Without a point
        // for every day, the chart would draw a slanting line between two event days across which
        // the estimated value does not actually move. So such a window gets every calendar day.
        if (fills.stream().anyMatch(fill -> closes.get(fill.symbol()) == null)) {
            for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
                days.add(day);
            }
        }

        Map<String, Long> held = new HashMap<>();
        Map<String, BigDecimal> lastTradePrice = new HashMap<>();
        Set<String> estimated = new HashSet<>();
        List<Point> points = new ArrayList<>();
        int ledgerIndex = 0;
        int fillIndex = 0;
        BigDecimal cash = BigDecimal.ZERO;

        for (LocalDate day : days) {
            while (ledgerIndex < ledger.size() && !ledger.get(ledgerIndex).date().isAfter(day)) {
                cash = ledger.get(ledgerIndex).balanceAfter();
                ledgerIndex++;
            }
            while (fillIndex < fills.size() && !fills.get(fillIndex).date().isAfter(day)) {
                Fill fill = fills.get(fillIndex);
                held.merge(fill.symbol(), fill.signedQuantity(), Long::sum);
                lastTradePrice.put(fill.symbol(), fill.price());
                fillIndex++;
            }

            BigDecimal invested = BigDecimal.ZERO;
            for (Map.Entry<String, Long> position : held.entrySet()) {
                if (position.getValue() == 0) {
                    continue;
                }
                String symbol = position.getKey();
                BigDecimal price = closeOn(closes.get(symbol), day);
                if (price == null) {
                    price = lastTradePrice.get(symbol);
                    estimated.add(symbol);
                }
                invested = invested.add(price.multiply(BigDecimal.valueOf(position.getValue())));
            }
            invested = invested.setScale(4, RoundingMode.HALF_UP);
            points.add(new Point(day, cash, invested, cash.add(invested)));
        }
        return new Result(points, estimated);
    }

    private static void addIfInWindow(TreeSet<LocalDate> days, LocalDate day, LocalDate from, LocalDate to) {
        if (!day.isBefore(from) && !day.isAfter(to)) {
            days.add(day);
        }
    }

    /** The last close on or before {@code day}, or null if there is none yet. */
    private static BigDecimal closeOn(NavigableMap<LocalDate, BigDecimal> series, LocalDate day) {
        if (series == null) {
            return null;
        }
        var entry = series.floorEntry(day);
        return entry == null ? null : entry.getValue();
    }
}
