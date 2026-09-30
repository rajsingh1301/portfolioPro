package com.portfoliopro.portfolio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One row of the cash ledger. `amount` is signed: positive for a deposit or a sell,
 * negative for a buy. Every change to a cash balance writes one, so the amounts sum to
 * the balance and each `balance_after` is the running total up to that row.
 */
@Entity
@Table(name = "cash_transactions")
public class CashTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_id")
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CashTransactionType type;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceAfter;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CashTransaction() {
        // for JPA
    }

    public CashTransaction(Long userId, Long orderId, CashTransactionType type, BigDecimal amount, BigDecimal balanceAfter) {
        this.userId = userId;
        this.orderId = orderId;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getUserId() {
        return userId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public CashTransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public java.time.Instant getCreatedAt() {
        return createdAt;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }
}
