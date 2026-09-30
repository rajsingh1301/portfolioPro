package com.portfoliopro.risk.dto;

import com.portfoliopro.risk.RiskLimits;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * A full replacement: all three limits every time, so a request can never leave a
 * limit half-specified. Decimal strings or numbers both bind to {@link BigDecimal}
 * exactly, with no float in between.
 */
public record UpdateRiskSettingsRequest(
        @NotNull(message = "is required")
        @DecimalMin(value = RiskLimits.MIN_POSITION_PCT, message = "must be between 1 and 100")
        @DecimalMax(value = RiskLimits.MAX_POSITION_PCT, message = "must be between 1 and 100")
        @Digits(integer = 3, fraction = 2, message = "at most 2 decimal places")
        BigDecimal maxPositionPct,

        @NotNull(message = "is required")
        @DecimalMin(value = RiskLimits.MIN_ORDER_VALUE, message = "must be between 1 and 1,000,000")
        @DecimalMax(value = RiskLimits.MAX_ORDER_VALUE, message = "must be between 1 and 1,000,000")
        @Digits(integer = 7, fraction = 2, message = "at most 2 decimal places")
        BigDecimal maxOrderValue,

        @NotNull(message = "is required")
        @DecimalMin(value = RiskLimits.MIN_STOP_LOSS_PCT, message = "must be between 0.5 and 50")
        @DecimalMax(value = RiskLimits.MAX_STOP_LOSS_PCT, message = "must be between 0.5 and 50")
        @Digits(integer = 2, fraction = 2, message = "at most 2 decimal places")
        BigDecimal defaultStopLossPct) {
}
