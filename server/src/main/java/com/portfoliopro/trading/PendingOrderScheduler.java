package com.portfoliopro.trading;

import com.portfoliopro.common.exception.ApiException;
import com.portfoliopro.market.MarketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Fills pending limit and stop-loss orders whose price has been reached. It reads
 * prices through {@link MarketService}, so it shares the quote cache and only adds a
 * Finnhub call for a symbol that has a pending order and whose cached quote has expired.
 */
@Component
public class PendingOrderScheduler {

    private static final Logger log = LoggerFactory.getLogger(PendingOrderScheduler.class);

    private final OrderRepository orderRepository;
    private final MarketService marketService;
    private final TradingService tradingService;
    private final boolean enabled;

    public PendingOrderScheduler(
            OrderRepository orderRepository,
            MarketService marketService,
            TradingService tradingService,
            @Value("${app.orders.poll-enabled:true}") boolean enabled) {
        this.orderRepository = orderRepository;
        this.marketService = marketService;
        this.tradingService = tradingService;
        this.enabled = enabled;
    }

    @Scheduled(
            fixedDelayString = "${app.orders.poll-interval-ms:10000}",
            initialDelayString = "${app.orders.poll-interval-ms:10000}")
    public void poll() {
        if (enabled) {
            runOnce();
        }
    }

    /**
     * One pass. Public so tests can drive it directly rather than wait on a timer.
     * A failure with one symbol or one order never stops the rest: each is isolated.
     */
    public void runOnce() {
        Map<String, List<Order>> bySymbol = orderRepository.findByStatus(OrderStatus.PENDING).stream()
                .collect(Collectors.groupingBy(Order::getSymbol));

        bySymbol.forEach((symbol, orders) -> {
            BigDecimal price;
            try {
                price = marketService.currentPrice(symbol);
            } catch (ApiException ex) {
                // No price this round: the orders stay pending and are tried again next pass.
                log.debug("Skipping {} pending orders for {}: {}", orders.size(), symbol, ex.getMessage());
                return;
            }
            for (Order order : orders) {
                if (!order.isTriggeredAt(price)) {
                    continue;
                }
                try {
                    tradingService.fillPending(order.getUserId(), order.getId(), price);
                } catch (RuntimeException ex) {
                    log.warn("Filling pending order {} failed", order.getId(), ex);
                }
            }
        });
    }
}
