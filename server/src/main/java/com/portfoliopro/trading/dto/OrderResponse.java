package com.portfoliopro.trading.dto;

import com.portfoliopro.trading.Order;

import java.time.Instant;

public record OrderResponse(
        Long id,
        String symbol,
        String side,
        String type,
        long quantity,
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
                order.getStatus().name(),
                order.getRejectReason(),
                order.getCreatedAt());
    }
}
