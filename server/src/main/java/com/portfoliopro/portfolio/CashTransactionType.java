package com.portfoliopro.portfolio;

import com.portfoliopro.common.OrderSide;

/** Why cash moved. The ledger's rows add up to the balance only if every movement has one. */
public enum CashTransactionType {
    /** Money in: the starting balance every account is opened with. */
    DEPOSIT,
    BUY,
    SELL;

    public static CashTransactionType of(OrderSide side) {
        return side == OrderSide.BUY ? BUY : SELL;
    }
}
