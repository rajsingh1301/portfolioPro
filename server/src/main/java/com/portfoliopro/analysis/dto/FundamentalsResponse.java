package com.portfoliopro.analysis.dto;

/**
 * Latest company ratios. Anything the provider does not report is absent rather than
 * zero. {@code marketCap} is whole US dollars; {@code roe} and {@code dividendYield}
 * are percentages.
 */
public record FundamentalsResponse(
        String symbol,
        String name,
        String exchange,
        String industry,
        String marketCap,
        String peRatio,
        String eps,
        String roe,
        String dividendYield,
        String week52High,
        String week52Low,
        String beta) {
}
