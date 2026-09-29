package com.portfoliopro.portfolio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;

/** One symbol a user follows. The key is the pair, so a symbol appears at most once per user. */
@Entity
@Table(name = "watchlist")
@IdClass(WatchlistEntry.Key.class)
public class WatchlistEntry {

    public record Key(Long userId, String symbol) implements Serializable {
    }

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Id
    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WatchlistEntry() {
        // for JPA
    }

    public WatchlistEntry(Long userId, String symbol) {
        this.userId = userId;
        this.symbol = symbol;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getUserId() {
        return userId;
    }

    public String getSymbol() {
        return symbol;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
