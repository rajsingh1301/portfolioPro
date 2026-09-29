package com.portfoliopro.portfolio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * A user's position in one symbol. A fully sold position stays as a row with quantity
 * zero so the realized P&L it booked is not lost.
 */
@Entity
@Table(name = "holdings")
@IdClass(Holding.Key.class)
public class Holding {

    public record Key(Long userId, String symbol) implements Serializable {
    }

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Id
    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false)
    private long quantity;

    @Column(name = "avg_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal avgPrice;

    @Column(name = "realized_pnl", nullable = false, precision = 19, scale = 4)
    private BigDecimal realizedPnl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Holding() {
        // for JPA
    }

    public static Holding empty(Long userId, String symbol) {
        Holding holding = new Holding();
        holding.userId = userId;
        holding.symbol = symbol;
        holding.quantity = 0;
        holding.avgPrice = BigDecimal.ZERO.setScale(4);
        holding.realizedPnl = BigDecimal.ZERO.setScale(4);
        return holding;
    }

    /** Volume-weighted average: the new average cost across old and new shares. */
    public void buy(long shares, BigDecimal price) {
        BigDecimal held = avgPrice.multiply(BigDecimal.valueOf(quantity));
        BigDecimal added = price.multiply(BigDecimal.valueOf(shares));
        long total = quantity + shares;
        this.avgPrice = held.add(added).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
        this.quantity = total;
    }

    /** Books {@code (price - avgPrice) * shares} as realized P&L. The average cost is unchanged. */
    public void sell(long shares, BigDecimal price) {
        BigDecimal gain = price.subtract(avgPrice).multiply(BigDecimal.valueOf(shares));
        this.realizedPnl = realizedPnl.add(gain).setScale(4, RoundingMode.HALF_UP);
        this.quantity -= shares;
        if (quantity == 0) {
            this.avgPrice = BigDecimal.ZERO.setScale(4);
        }
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getUserId() {
        return userId;
    }

    public String getSymbol() {
        return symbol;
    }

    public long getQuantity() {
        return quantity;
    }

    public BigDecimal getAvgPrice() {
        return avgPrice;
    }

    public BigDecimal getRealizedPnl() {
        return realizedPnl;
    }
}
