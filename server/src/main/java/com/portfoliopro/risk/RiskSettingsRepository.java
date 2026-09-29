package com.portfoliopro.risk;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RiskSettingsRepository extends JpaRepository<RiskSettings, Long> {

    Optional<RiskSettings> findByUserId(Long userId);
}
