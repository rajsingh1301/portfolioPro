package com.portfoliopro.analysis;

import com.portfoliopro.analysis.dto.Reading;
import com.portfoliopro.market.Candle;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Plain-words descriptions of where the indicators stand now. Rule 8 governs every
 * string here: they describe, and never advise. "Overbought zone" says where the RSI
 * is; it does not say what to do, and indicators are frequently wrong.
 */
final class Readings {

    static final BigDecimal OVERBOUGHT = BigDecimal.valueOf(70);
    static final BigDecimal OVERSOLD = BigDecimal.valueOf(30);
    /** How far back a crossing of the two averages still counts as recent. */
    private static final int RECENT_BARS = 5;

    private Readings() {
    }

    static List<Reading> describe(List<Candle> candles, IndicatorCalculator.Computed c, String barUnit) {
        List<Reading> readings = new ArrayList<>();
        if (candles.isEmpty()) {
            return readings;
        }
        int last = candles.size() - 1;
        BigDecimal close = candles.get(last).close();

        BigDecimal rsi = c.rsi14().get(last);
        if (rsi != null) {
            String value = rsi.setScale(1, RoundingMode.HALF_UP).toPlainString();
            String zone = rsi.compareTo(OVERBOUGHT) >= 0 ? "overbought zone"
                    : rsi.compareTo(OVERSOLD) <= 0 ? "oversold zone" : "neutral range";
            readings.add(new Reading("RSI (14)", "RSI is " + value + ", in the " + zone));
        }

        BigDecimal histogram = c.macdHistogram().get(last);
        if (histogram != null) {
            BigDecimal previous = last > 0 ? c.macdHistogram().get(last - 1) : null;
            String label;
            if (previous != null && previous.signum() <= 0 && histogram.signum() > 0) {
                label = "MACD crossed above its signal line";
            } else if (previous != null && previous.signum() >= 0 && histogram.signum() < 0) {
                label = "MACD crossed below its signal line";
            } else {
                label = histogram.signum() >= 0 ? "MACD is above its signal line" : "MACD is below its signal line";
            }
            readings.add(new Reading("MACD (12, 26, 9)", label));
        }

        BigDecimal sma50 = c.sma50().get(last);
        if (sma50 != null) {
            String side = close.compareTo(sma50) >= 0 ? "above" : "below";
            readings.add(new Reading("Moving average (50)", "Price is " + side + " its 50-" + barUnit + " average"));
        }

        String cross = averagesCross(c, last, barUnit);
        if (cross != null) {
            readings.add(new Reading("Moving averages (20, 50)", cross));
        }

        BigDecimal upper = c.bollingerUpper().get(last);
        BigDecimal lower = c.bollingerLower().get(last);
        if (upper != null && lower != null) {
            String position = close.compareTo(upper) > 0 ? "above the upper band"
                    : close.compareTo(lower) < 0 ? "below the lower band" : "within the bands";
            readings.add(new Reading("Bollinger Bands (20, 2)", "Price is " + position));
        }
        return readings;
    }

    /** Whether the 20-bar average crossed the 50-bar average within the last few bars, and which way. */
    private static String averagesCross(IndicatorCalculator.Computed c, int last, String barUnit) {
        for (int i = last; i > Math.max(last - RECENT_BARS, 0); i--) {
            BigDecimal shortNow = c.sma20().get(i);
            BigDecimal longNow = c.sma50().get(i);
            BigDecimal shortBefore = c.sma20().get(i - 1);
            BigDecimal longBefore = c.sma50().get(i - 1);
            if (shortNow == null || longNow == null || shortBefore == null || longBefore == null) {
                continue;
            }
            int now = shortNow.compareTo(longNow);
            int before = shortBefore.compareTo(longBefore);
            if (before <= 0 && now > 0) {
                return "The 20-" + barUnit + " average crossed above the 50-" + barUnit + " average recently";
            }
            if (before >= 0 && now < 0) {
                return "The 20-" + barUnit + " average crossed below the 50-" + barUnit + " average recently";
            }
        }
        return null;
    }
}
