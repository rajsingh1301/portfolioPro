package com.portfoliopro.trading.dto;

import com.portfoliopro.common.OrderSide;
import com.portfoliopro.trading.OrderType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record PlaceOrderRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9.\\-]{1,20}", message = "must be a ticker symbol") String symbol,
        @NotNull OrderSide side,
        @NotNull OrderType type,
        // Whole shares only; the cap keeps quantity * price far inside DECIMAL(19,4).
        @NotNull @Min(1) @Max(1_000_000) Long quantity) {
}
