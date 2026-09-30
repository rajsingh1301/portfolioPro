package com.portfoliopro.risk.dto;

import com.portfoliopro.risk.RiskLimits;
import com.portfoliopro.risk.RiskSettings;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The user's limits together with the bounds and defaults, so the screen can show what
 * is allowed without hard-coding it. Everything is a string (rule 1).
 */
public record RiskSettingsResponse(
        String maxPositionPct,
        String maxOrderValue,
        String defaultStopLossPct,
        Bounds bounds,
        Defaults defaults) {

    public record Range(String min, String max) {
    }

    public record Bounds(Range maxPositionPct, Range maxOrderValue, Range defaultStopLossPct) {
    }

    public record Defaults(String maxPositionPct, String maxOrderValue, String defaultStopLossPct) {
    }

    public static RiskSettingsResponse from(RiskSettings settings) {
        return new RiskSettingsResponse(
                fixed(settings.getMaxPositionPct()),
                fixed(settings.getMaxOrderValue()),
                fixed(settings.getDefaultStopLossPct()),
                new Bounds(range(RiskLimits.POSITION_PCT), range(RiskLimits.ORDER_VALUE), range(RiskLimits.STOP_LOSS_PCT)),
                new Defaults(
                        fixed(RiskSettings.DEFAULT_MAX_POSITION_PCT),
                        fixed(RiskSettings.DEFAULT_MAX_ORDER_VALUE),
                        fixed(RiskSettings.DEFAULT_STOP_LOSS_PCT)));
    }

    private static Range range(BigDecimal[] bounds) {
        return new Range(fixed(bounds[0]), fixed(bounds[1]));
    }

    private static String fixed(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
