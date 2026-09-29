package com.portfoliopro.market;

import java.math.BigDecimal;
import java.time.Instant;

/** One OHLCV bar. Prices are decimal: rule 1 applies to history as much as to a quote. */
public record Candle(Instant time, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, long volume) {
}
