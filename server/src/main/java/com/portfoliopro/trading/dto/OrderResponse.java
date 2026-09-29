package com.portfoliopro.trading.dto;

import com.portfoliopro.trading.Order;

import java.time.Instant;

/** Prices are strings, and absent (not null) when the order type has none. */
public record OrderResponse(
        Long id,
        String symbol,
        String side,
        String type,
        long quantity,
        String limitPrice,
        String triggerPrice,
        boolean attachStopLoss,
        String status,
        String rejectReason,
        Instant createdAt) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getSymbol(),
                order.getSide().name(),
                order.getType().name(),
                order.getQuantity(),
                order.getLimitPrice() == null ? null : order.getLimitPrice().toPlainString(),
                order.getTriggerPrice() == null ? null : order.getTriggerPrice().toPlainString(),
                order.isAttachStopLoss(),
                order.getStatus().name(),
                order.getRejectReason(),
                order.getCreatedAt());
    }
}
