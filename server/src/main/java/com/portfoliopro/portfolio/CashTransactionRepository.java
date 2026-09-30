package com.portfoliopro.portfolio;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface CashTransactionRepository extends JpaRepository<CashTransaction, Long> {

    /** In the order the movements happened. */
    List<CashTransaction> findByUserIdOrderByIdAsc(Long userId);

    /** Money put into the account: the sum of its deposits. */
    @Query("""
            select coalesce(sum(c.amount), 0) from CashTransaction c
            where c.userId = :userId and c.type = com.portfoliopro.portfolio.CashTransactionType.DEPOSIT""")
    BigDecimal netDeposits(@Param("userId") Long userId);
}
