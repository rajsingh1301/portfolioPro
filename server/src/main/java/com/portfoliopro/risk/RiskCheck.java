package com.portfoliopro.risk;

import com.portfoliopro.common.OrderSide;

import java.math.BigDecimal;

/**
 * Everything the risk rules need to judge one order, gathered by the caller. Passing
 * facts in keeps {@code risk} from reaching into holdings itself, which the module
 * dependency rule would not allow.
 *
 * @param cash                 the user's cash balance, read under the row lock
 * @param sharesHeld           shares already held in this symbol
 * @param otherHoldingsAtCost  every other position, valued at cost so that checking one
 *                             order does not cost a Finnhub call per holding
 */
public record RiskCheck(
        OrderSide side,
        long quantity,
        BigDecimal price,
        BigDecimal cash,
        long sharesHeld,
        BigDecimal otherHoldingsAtCost) {
}
