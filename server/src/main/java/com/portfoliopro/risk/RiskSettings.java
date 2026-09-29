package com.portfoliopro.risk;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Per-user trading limits. Created with the defaults at signup so that slice 3 can
 * assume every user has exactly one row and never has to handle a missing one.
 */
@Entity
@Table(name = "risk_settings")
public class RiskSettings {

    /** 20% of the portfolio in a single stock. */
    public static final BigDecimal DEFAULT_MAX_POSITION_PCT = new BigDecimal("20.00");

    /** $5,000 per order. */
    public static final BigDecimal DEFAULT_MAX_ORDER_VALUE = new BigDecimal("5000.0000");

    /** 5% below the fill price. */
    public static final BigDecimal DEFAULT_STOP_LOSS_PCT = new BigDecimal("5.00");

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "max_position_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxPositionPct;

    @Column(name = "max_order_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal maxOrderValue;

    @Column(name = "default_stop_loss_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal defaultStopLossPct;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RiskSettings() {
        // for JPA
    }

    public static RiskSettings defaultsFor(Long userId) {
        RiskSettings settings = new RiskSettings();
        settings.userId = userId;
        settings.maxPositionPct = DEFAULT_MAX_POSITION_PCT;
        settings.maxOrderValue = DEFAULT_MAX_ORDER_VALUE;
        settings.defaultStopLossPct = DEFAULT_STOP_LOSS_PCT;
        return settings;
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

    public BigDecimal getMaxPositionPct() {
        return maxPositionPct;
    }

    public void setMaxPositionPct(BigDecimal maxPositionPct) {
        this.maxPositionPct = maxPositionPct;
    }

    public BigDecimal getMaxOrderValue() {
        return maxOrderValue;
    }

    public void setMaxOrderValue(BigDecimal maxOrderValue) {
        this.maxOrderValue = maxOrderValue;
    }

    public BigDecimal getDefaultStopLossPct() {
        return defaultStopLossPct;
    }

    public void setDefaultStopLossPct(BigDecimal defaultStopLossPct) {
        this.defaultStopLossPct = defaultStopLossPct;
    }
}
