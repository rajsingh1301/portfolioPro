package com.portfoliopro.risk;

import java.math.BigDecimal;

/**
 * The bounds a user may set their limits within, and the defaults new accounts start
 * with. One place, so the validation, the API's description of the bounds and the
 * screen that shows them can never disagree.
 *
 * <p>The lower bounds stop a zero or negative value silently blocking every trade; the
 * upper bounds keep figures inside their database columns and stop a limit being set to
 * something meaningless. Strings, because annotation values must be constants.
 */
public final class RiskLimits {

    public static final String MIN_POSITION_PCT = "1";
    public static final String MAX_POSITION_PCT = "100";
    public static final String MIN_ORDER_VALUE = "1";
    public static final String MAX_ORDER_VALUE = "1000000";
    public static final String MIN_STOP_LOSS_PCT = "0.5";
    public static final String MAX_STOP_LOSS_PCT = "50";

    public static final BigDecimal[] POSITION_PCT = {new BigDecimal(MIN_POSITION_PCT), new BigDecimal(MAX_POSITION_PCT)};
    public static final BigDecimal[] ORDER_VALUE = {new BigDecimal(MIN_ORDER_VALUE), new BigDecimal(MAX_ORDER_VALUE)};
    public static final BigDecimal[] STOP_LOSS_PCT = {new BigDecimal(MIN_STOP_LOSS_PCT), new BigDecimal(MAX_STOP_LOSS_PCT)};

    private RiskLimits() {
    }
}
