package com.portfoliopro.portfolio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WatchlistRepository extends JpaRepository<WatchlistEntry, WatchlistEntry.Key> {

    /** In the order the user added them. */
    List<WatchlistEntry> findByUserIdOrderByCreatedAtAscSymbolAsc(Long userId);

    long countByUserId(Long userId);

    boolean existsByUserIdAndSymbol(Long userId, String symbol);

    long deleteByUserIdAndSymbol(Long userId, String symbol);
}
