package com.portfoliopro.trading;

import java.util.Arrays;
import java.util.Optional;

/** The windows the performance view offers. A closed set, like the chart ranges. */
public enum PerformanceRange {

    MONTH("1M", 30),
    QUARTER("3M", 90),
    HALF_YEAR("6M", 180),
    YEAR("1Y", 365);

    public static final String PATTERN = "1M|3M|6M|1Y";

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
