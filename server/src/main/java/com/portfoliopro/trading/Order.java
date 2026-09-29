package com.portfoliopro.trading;

import com.portfoliopro.common.OrderSide;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private OrderSide side;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OrderType type;

    @Column(nullable = false)
    private long quantity;

    @Column(name = "limit_price", precision = 19, scale = 4)
    private BigDecimal limitPrice;

    @Column(name = "trigger_price", precision = 19, scale = 4)
    private BigDecimal triggerPrice;

    @Column(name = "attach_stop_loss", nullable = false)
    private boolean attachStopLoss;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OrderStatus status;

    @Column(name = "reject_reason", length = 100)
    private String rejectReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Order() {
        // for JPA
    }

    public Order(Long userId, String symbol, OrderSide side, OrderType type, long quantity) {
        this.userId = userId;
        this.symbol = symbol;
        this.side = side;
        this.type = type;
        this.quantity = quantity;
        this.status = OrderStatus.PENDING;
    }

    public static Order limit(
            Long userId, String symbol, OrderSide side, long quantity, BigDecimal limitPrice, boolean attachStopLoss) {
        Order order = new Order(userId, symbol, side, OrderType.LIMIT, quantity);
        order.limitPrice = scaled(limitPrice);
        order.attachStopLoss = attachStopLoss;
        return order;
    }

    public static Order stopLoss(Long userId, String symbol, long quantity, BigDecimal triggerPrice) {
        Order order = new Order(userId, symbol, OrderSide.SELL, OrderType.STOP_LOSS, quantity);
        order.triggerPrice = scaled(triggerPrice);
        return order;
    }

    public static Order market(Long userId, String symbol, OrderSide side, long quantity, boolean attachStopLoss) {
        Order order = new Order(userId, symbol, side, OrderType.MARKET, quantity);
        order.attachStopLoss = attachStopLoss;
        return order;
    }

    /** Held at the column's scale, so an order reads the same before and after a round trip to MySQL. */
    private static BigDecimal scaled(BigDecimal price) {
        return price.setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Whether this pending order should fill at {@code price}: a buy limit at or below
     * its limit, a sell limit at or above it, a stop-loss once the price has fallen to
     * its trigger. A market order is never pending, so it is never triggered here.
     */
    public boolean isTriggeredAt(BigDecimal price) {
        return switch (type) {
            case MARKET -> false;
            case LIMIT -> side == OrderSide.BUY ? price.compareTo(limitPrice) <= 0 : price.compareTo(limitPrice) >= 0;
            case STOP_LOSS -> price.compareTo(triggerPrice) <= 0;
        };
    }

    /** The price the order is willing to trade at, for risk-checking it before it can fill. */
    public BigDecimal referencePrice(BigDecimal marketPrice) {
        return switch (type) {
            case MARKET -> marketPrice;
            case LIMIT -> limitPrice;
            case STOP_LOSS -> triggerPrice;
        };
    }

    public void cancel() {
        this.status = OrderStatus.CANCELLED;
    }

    public void fill() {
        this.status = OrderStatus.FILLED;
    }

    public void reject(String reason) {
        this.status = OrderStatus.REJECTED;
        this.rejectReason = reason;
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

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getSymbol() {
        return symbol;
    }

    public OrderSide getSide() {
        return side;
    }

    public OrderType getType() {
        return type;
    }

    public long getQuantity() {
        return quantity;
    }

    public BigDecimal getLimitPrice() {
        return limitPrice;
    }

    public BigDecimal getTriggerPrice() {
        return triggerPrice;
    }

    public boolean isAttachStopLoss() {
        return attachStopLoss;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
