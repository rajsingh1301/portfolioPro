package com.portfoliopro.trading;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUserIdOrderByIdDesc(Long userId);

    List<Order> findByStatus(OrderStatus status);

    /**
     * Shares already promised to other pending sells in this symbol, so a stop-loss and
     * a limit sell cannot both claim the same shares. {@code excludeId} leaves out the
     * order being judged, which must not count against itself.
     */
    @Query("""
            select coalesce(sum(o.quantity), 0) from Order o
            where o.userId = :userId and o.symbol = :symbol
              and o.side = com.portfoliopro.common.OrderSide.SELL
              and o.status = com.portfoliopro.trading.OrderStatus.PENDING
              and o.id <> :excludeId""")
    long pendingSellQuantity(
            @Param("userId") Long userId, @Param("symbol") String symbol, @Param("excludeId") Long excludeId);
}
