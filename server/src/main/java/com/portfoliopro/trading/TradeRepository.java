package com.portfoliopro.trading;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TradeRepository extends JpaRepository<Trade, Long> {

    List<Trade> findByUserIdOrderByIdDesc(Long userId);
}
