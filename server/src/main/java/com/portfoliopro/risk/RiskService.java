package com.portfoliopro.risk;

import com.portfoliopro.common.OrderSide;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Pre-trade checks (rule 4: they run before execution, never after). Pure: it reads
 * nothing itself, so the caller decides which state, read under which lock, is judged.
 */
@Service
public class RiskService {

    static final String INSUFFICIENT_BALANCE = "insufficient balance";
    static final String NOT_ENOUGH_SHARES = "not enough shares";
    static final String POSITION_LIMIT = "position limit";
    static final String ORDER_TOO_LARGE = "order too large";

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** The reason the order must be rejected, or empty if it may proceed. */
    public Optional<String> reject(RiskCheck check, RiskSettings settings) {
        BigDecimal orderValue = orderValue(check.price(), check.quantity());

        if (check.side() == OrderSide.SELL && check.quantity() > check.sharesHeld()) {
            return Optional.of(NOT_ENOUGH_SHARES);
        }
        if (check.side() == OrderSide.BUY && orderValue.compareTo(check.cash()) > 0) {
            return Optional.of(INSUFFICIENT_BALANCE);
        }
        if (orderValue.compareTo(settings.getMaxOrderValue()) > 0) {
            return Optional.of(ORDER_TOO_LARGE);
        }
        if (check.side() == OrderSide.BUY && breachesPositionLimit(check, settings)) {
            return Optional.of(POSITION_LIMIT);
        }
        return Optional.empty();
    }

    /**
     * A buy turns cash into shares, so total portfolio value does not change: it is
     * the cash, the other positions at cost, and this symbol at the current price.
     */
    private boolean breachesPositionLimit(RiskCheck check, RiskSettings settings) {
        BigDecimal symbolAfter = orderValue(check.price(), check.sharesHeld() + check.quantity());
        BigDecimal symbolBefore = orderValue(check.price(), check.sharesHeld());
        BigDecimal portfolio = check.cash().add(check.otherHoldingsAtCost()).add(symbolBefore);
        BigDecimal limit = portfolio.multiply(settings.getMaxPositionPct()).divide(HUNDRED, 4, RoundingMode.HALF_UP);
        return symbolAfter.compareTo(limit) > 0;
    }

    public static BigDecimal orderValue(BigDecimal price, long quantity) {
        return price.multiply(BigDecimal.valueOf(quantity)).setScale(4, RoundingMode.HALF_UP);
    }
}
