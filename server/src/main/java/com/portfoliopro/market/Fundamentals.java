package com.portfoliopro.market;

import java.math.BigDecimal;

/**
 * Finnhub's company profile and headline ratios, combined. Every field is optional:
 * the provider reports what it has. {@code marketCap} is whole US dollars, converted
 * from the millions Finnhub sends; {@code roe} and {@code dividendYield} are percentages.
 */
public record Fundamentals(
        String name,
        String exchange,
        String industry,
        BigDecimal marketCap,
        BigDecimal peRatio,
        BigDecimal eps,
        BigDecimal roe,
        BigDecimal dividendYield,
        BigDecimal week52High,
        BigDecimal week52Low,
        BigDecimal beta) {

    /** Finnhub answers a symbol it does not carry with empty objects rather than a 404. */
    public boolean isEmpty() {
        return name == null && marketCap == null && peRatio == null && eps == null && week52High == null;
    }
}
