package com.portfoliopro.trading;

public enum OrderType {
    /** Fills at once at the current price. */
    MARKET,
    /** Waits until a buy is at or below, or a sell at or above, its limit price. */
    LIMIT,
    /** A sell that waits until the price falls to its trigger. */
    STOP_LOSS
}
