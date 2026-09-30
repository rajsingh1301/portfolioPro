package com.portfoliopro.portfolio;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CashTransactionRepository extends JpaRepository<CashTransaction, Long> {

    /** In the order the movements happened. */
    java.util.List<CashTransaction> findByUserIdOrderByIdAsc(Long userId);
}
