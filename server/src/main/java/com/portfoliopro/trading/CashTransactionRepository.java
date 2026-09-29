package com.portfoliopro.trading;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CashTransactionRepository extends JpaRepository<CashTransaction, Long> {
}
