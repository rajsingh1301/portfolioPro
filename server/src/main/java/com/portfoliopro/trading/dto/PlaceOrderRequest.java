package com.portfoliopro.trading.dto;

import com.portfoliopro.common.OrderSide;
import com.portfoliopro.trading.OrderType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/**
 * Which price fields go with which type is checked in the service, where the failure
 * can name the combination: a limit needs {@code limitPrice}, a stop-loss needs
 * {@code triggerPrice} and is a sell, and a market order takes neither.
 */
public record PlaceOrderRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9.\\-]{1,20}", message = "must be a ticker symbol") String symbol,
        @NotNull OrderSide side,
        @NotNull OrderType type,
        // Whole shares only; the cap keeps quantity * price far inside DECIMAL(19,4).
        @NotNull @Min(1) @Max(1_000_000) Long quantity,
        @DecimalMin(value = "0.0001", message = "must be positive")
        @Digits(integer = 12, fraction = 4, message = "at most 4 decimal places") BigDecimal limitPrice,
        @DecimalMin(value = "0.0001", message = "must be positive")
        @Digits(integer = 12, fraction = 4, message = "at most 4 decimal places") BigDecimal triggerPrice,
        /** On a buy: once it fills, also place a stop-loss below the fill price. */
        Boolean attachStopLoss) {
}
