package com.portfoliopro.portfolio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HoldingRepository extends JpaRepository<Holding, Holding.Key> {

    Optional<Holding> findByUserIdAndSymbol(Long userId, String symbol);

    List<Holding> findByUserId(Long userId);
}
