package com.portfoliopro.market;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Finnhub's `/quote` payload, with its one-letter fields named. Decimal, not double:
 * the numbers are prices and rule 1 applies from the moment they enter the process.
 *
 * <p>Finnhub answers an unknown symbol with every field zero rather than a 404, so
 * {@link #isEmpty()} is what "no such symbol" looks like here.
 */
public record FinnhubQuote(
        BigDecimal current,
        BigDecimal change,
        BigDecimal percentChange,
        BigDecimal high,
        BigDecimal low,
        BigDecimal open,
        BigDecimal previousClose,
        Instant timestamp) {

    public boolean isEmpty() {
        return current == null || current.signum() == 0;
    }
}
