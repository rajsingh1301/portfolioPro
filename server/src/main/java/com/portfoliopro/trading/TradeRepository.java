package com.portfoliopro.trading;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TradeRepository extends JpaRepository<Trade, Long> {

    List<Trade> findByUserIdOrderByIdDesc(Long userId);

    /** In the order they happened, which is the order performance replays them in. */
    List<Trade> findByUserIdOrderByIdAsc(Long userId);
}
