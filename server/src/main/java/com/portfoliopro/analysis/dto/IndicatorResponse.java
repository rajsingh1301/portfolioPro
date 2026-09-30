package com.portfoliopro.analysis.dto;

import java.util.List;

/**
 * Every series is aligned to the candles of the same range; a series starts later than
 * the candles where the indicator needs history first (a 50-bar average has no value
 * for the first 49 bars). Values are strings, prices to four places.
 */
public record IndicatorResponse(
        String symbol,
        String range,
        List<IndicatorPoint> sma20,
        List<IndicatorPoint> sma50,
        List<IndicatorPoint> ema20,
        List<IndicatorPoint> rsi14,
        Macd macd,
        Bollinger bollinger,
        List<Reading> readings,
        String disclaimer) {

    public record Macd(List<IndicatorPoint> line, List<IndicatorPoint> signal, List<IndicatorPoint> histogram) {
    }

    public record Bollinger(List<IndicatorPoint> upper, List<IndicatorPoint> middle, List<IndicatorPoint> lower) {
    }
}
