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
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** One row of the cash ledger. `amount` is signed: negative for a buy, positive for a sell. */
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
    private OrderSide type;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceAfter;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CashTransaction() {
        // for JPA
    }

    public CashTransaction(Long userId, Long orderId, OrderSide type, BigDecimal amount, BigDecimal balanceAfter) {
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

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }
}
