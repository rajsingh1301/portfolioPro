package com.portfoliopro.risk;

import com.portfoliopro.common.OrderSide;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Risk rules")
class RiskServiceTest {

    private final RiskService risk = new RiskService();
    private final RiskSettings settings = RiskSettings.defaultsFor(1L);

    private static RiskCheck buy(long qty, String price, String cash, long held, String otherAtCost) {
        return new RiskCheck(OrderSide.BUY, qty, new BigDecimal(price), new BigDecimal(cash), held, new BigDecimal(otherAtCost));
    }

    @Test
    @DisplayName("a buy within every limit passes")
    void passes() {
        assertThat(risk.reject(buy(10, "100.00", "100000", 0, "0"), settings)).isEmpty();
    }

    @Test
    @DisplayName("a buy costing more than cash is rejected for balance, ahead of the size limit")
    void insufficientBalance() {
        assertThat(risk.reject(buy(10, "100.00", "999.99", 0, "0"), settings)).contains("insufficient balance");
    }

    @Test
    @DisplayName("an order over the max order value is rejected, and exactly at it passes")
    void orderTooLarge() {
        assertThat(risk.reject(buy(51, "100.00", "100000", 0, "0"), settings)).contains("order too large");
        assertThat(risk.reject(buy(50, "100.00", "100000", 0, "0"), settings)).isEmpty();
    }

    @Test
    @DisplayName("a buy taking one stock past 20% of the portfolio is rejected, exactly 20% passes")
    void positionLimit() {
        settings.setMaxOrderValue(new BigDecimal("1000000"));
        // Portfolio is 100,000 either way, so the limit is 20,000.
        assertThat(risk.reject(buy(201, "100.00", "100000", 0, "0"), settings)).contains("position limit");
        assertThat(risk.reject(buy(200, "100.00", "100000", 0, "0"), settings)).isEmpty();
        // Already holding 150 shares (15,000): 51 more crosses, 50 more lands on the limit.
        assertThat(risk.reject(buy(51, "100.00", "85000", 150, "0"), settings)).contains("position limit");
        assertThat(risk.reject(buy(50, "100.00", "85000", 150, "0"), settings)).isEmpty();
    }

    @Test
    @DisplayName("other holdings count towards the portfolio the limit is a share of")
    void otherHoldingsWiden() {
        settings.setMaxOrderValue(new BigDecimal("1000000"));
        // 50,000 cash + 50,000 elsewhere = 100,000 portfolio, so 20,000 is the limit.
        assertThat(risk.reject(buy(201, "100.00", "50000", 0, "50000"), settings)).contains("position limit");
        assertThat(risk.reject(buy(200, "100.00", "50000", 0, "50000"), settings)).isEmpty();
    }

    @Test
    @DisplayName("a sell of more than is held is rejected; a sell is not subject to the position limit or balance")
    void sells() {
        RiskCheck tooMany = new RiskCheck(OrderSide.SELL, 11, new BigDecimal("100"), BigDecimal.ZERO, 10, BigDecimal.ZERO);
        assertThat(risk.reject(tooMany, settings)).contains("not enough shares");
        RiskCheck fine = new RiskCheck(OrderSide.SELL, 10, new BigDecimal("100"), BigDecimal.ZERO, 10, BigDecimal.ZERO);
        assertThat(risk.reject(fine, settings)).isEmpty();
    }

    @Test
    @DisplayName("a sell over the max order value is rejected")
    void sellTooLarge() {
        RiskCheck big = new RiskCheck(OrderSide.SELL, 100, new BigDecimal("100"), BigDecimal.ZERO, 100, BigDecimal.ZERO);
        assertThat(risk.reject(big, settings)).contains("order too large");
    }
}
