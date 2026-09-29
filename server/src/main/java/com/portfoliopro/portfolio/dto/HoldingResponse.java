package com.portfoliopro.portfolio.dto;

/**
 * One open position. {@code price} and {@code unrealizedPnl} are null when the quote
 * could not be fetched; {@code marketValue} then falls back to cost so totals stay
 * usable, and the null price is how the client knows it is an estimate.
 */
public record HoldingResponse(
        String symbol,
        long quantity,
        String avgPrice,
        String price,
        String marketValue,
        String unrealizedPnl,
        String unrealizedPnlPercent,
        String realizedPnl) {
}
