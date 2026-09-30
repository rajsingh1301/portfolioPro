package com.portfoliopro.analysis.dto;

/** One value of an indicator at one candle. {@code time} matches the candle's, UTC epoch seconds. */
public record IndicatorPoint(long time, String value) {
}
