package com.portfoliopro.trading;

import java.util.Arrays;
import java.util.Optional;

/** The windows the performance view offers. A closed set, like the chart ranges. */
public enum PerformanceRange {

    WEEK("1W", 7),
    MONTH("1M", 30),
    QUARTER("3M", 90),
    HALF_YEAR("6M", 180),
    YEAR("1Y", 365),
    /** Since the account opened; the window is clamped to that day, so this is simply "all of it". */
    ALL("ALL", 3650);

    public static final String PATTERN = "1W|1M|3M|6M|1Y|ALL";

    private final String label;
    private final int days;

    PerformanceRange(String label, int days) {
        this.label = label;
        this.days = days;
    }

    public String label() {
        return label;
    }

    public int days() {
        return days;
    }

    public static Optional<PerformanceRange> fromLabel(String label) {
        return Arrays.stream(values()).filter(range -> range.label.equals(label)).findFirst();
    }
}
