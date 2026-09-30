package com.portfoliopro.market;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * The chart ranges the API offers. A closed set on purpose: the client picks a label,
 * never a provider interval or size, so a caller cannot spend the provider's small
 * daily quota on arbitrary requests.
 */
public enum CandleRange {

    DAY("1D", "5min", 80, Duration.ofMinutes(5), "5-minute"),
    WEEK("1W", "30min", 70, Duration.ofMinutes(30), "30-minute"),
    MONTH("1M", "1day", 22, Duration.ofDays(1), "day"),
    HALF_YEAR("6M", "1day", 130, Duration.ofDays(1), "day"),
    YEAR("1Y", "1day", 252, Duration.ofDays(1), "day"),
    FIVE_YEARS("5Y", "1week", 260, Duration.ofDays(7), "week");

    public static final String PATTERN = "1D|1W|1M|6M|1Y|5Y";

    private final String label;
    private final String interval;
    private final int size;
    private final Duration barDuration;
    private final String barUnit;

    CandleRange(String label, String interval, int size, Duration barDuration, String barUnit) {
        this.label = label;
        this.interval = interval;
        this.size = size;
        this.barDuration = barDuration;
        this.barUnit = barUnit;
    }

    /** How long one candle spans. */
    public Duration barDuration() {
        return barDuration;
    }

    /** What one candle is called in plain words, e.g. "day" for "a 50-day average". */
    public String barUnit() {
        return barUnit;
    }

    public String label() {
        return label;
    }

    /** Twelve Data's name for the candle width. */
    public String interval() {
        return interval;
    }

    /** How many candles cover the range. */
    public int size() {
        return size;
    }

    public static Optional<CandleRange> fromLabel(String label) {
        return Arrays.stream(values()).filter(range -> range.label.equals(label)).findFirst();
    }
}
