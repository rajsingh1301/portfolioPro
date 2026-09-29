package com.portfoliopro.trading.dto;

import com.portfoliopro.trading.Trade;

import java.time.Instant;

/** Money leaves as a string: rule 1 holds over the wire as well as in the database. */
public record TradeResponse(
        Long id,
        Long orderId,
        String symbol,
        String side,
        long quantity,
        String price,
        Instant executedAt) {

    public static TradeResponse from(Trade trade) {
        return new TradeResponse(
                trade.getId(),
                trade.getOrderId(),
                trade.getSymbol(),
                trade.getSide().name(),
                trade.getQuantity(),
                trade.getPrice().toPlainString(),
                trade.getExecutedAt());
    }
}
