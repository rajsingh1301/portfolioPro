package com.portfoliopro.market.dto;

import java.time.Instant;

/**
 * A single live quote. Every price is a string for the reason in ARCHITECTURE rule 1
 * — a JSON number would reach the browser as a float.
 *
 * @param asOf the provider's own timestamp for the quote, not the time we served it
 */
public record QuoteResponse(
        String symbol,
        String price,
        String change,
        String percentChange,
        String high,
        String low,
        String open,
        String previousClose,
        Instant asOf) {
}
