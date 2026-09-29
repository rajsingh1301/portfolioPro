package com.portfoliopro.trading;

import com.portfoliopro.auth.User;
import com.portfoliopro.auth.UserRepository;
import com.portfoliopro.common.OrderSide;
import com.portfoliopro.common.exception.InvalidOrderException;
import com.portfoliopro.common.exception.NotFoundException;
import com.portfoliopro.common.exception.OrderNotPendingException;
import com.portfoliopro.common.exception.OrderRejectedException;
import com.portfoliopro.market.MarketService;
import com.portfoliopro.portfolio.Holding;
import com.portfoliopro.portfolio.HoldingRepository;
import com.portfoliopro.risk.RiskCheck;
import com.portfoliopro.risk.RiskService;
import com.portfoliopro.risk.RiskSettings;
import com.portfoliopro.risk.RiskSettingsRepository;
import com.portfoliopro.trading.dto.OrderResponse;
import com.portfoliopro.trading.dto.PlaceOrderRequest;
import com.portfoliopro.trading.dto.TradeResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * Every write to a user's cash, holdings or orders goes through here, and every one
 * begins by locking that user's row. That single discipline is what makes a fill, a
 * cancel and a new order safe against each other: whichever gets the lock first
 * finishes before the next reads anything.
 *
 * <p>Nothing may read before the lock. MySQL fixes a transaction's snapshot at its
 * first plain SELECT, so an order read ahead of the lock could show a status that a
 * concurrent cancel has already changed. The lock is a locking read and sees the
 * latest committed rows; the plain reads after it then take a fresh snapshot.
 */
@Service
public class TradingService {

    private static final Logger log = LoggerFactory.getLogger(TradingService.class);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final MarketService marketService;
    private final RiskService riskService;
    private final UserRepository userRepository;
    private final RiskSettingsRepository riskSettingsRepository;
    private final HoldingRepository holdingRepository;
    private final OrderRepository orderRepository;
    private final TradeRepository tradeRepository;
    private final CashTransactionRepository cashTransactionRepository;
    private final TransactionTemplate transaction;

    public TradingService(
            MarketService marketService,
            RiskService riskService,
            UserRepository userRepository,
            RiskSettingsRepository riskSettingsRepository,
            HoldingRepository holdingRepository,
            OrderRepository orderRepository,
            TradeRepository tradeRepository,
            CashTransactionRepository cashTransactionRepository,
            PlatformTransactionManager transactionManager) {
        this.marketService = marketService;
        this.riskService = riskService;
        this.userRepository = userRepository;
        this.riskSettingsRepository = riskSettingsRepository;
        this.holdingRepository = holdingRepository;
        this.orderRepository = orderRepository;
        this.tradeRepository = tradeRepository;
        this.cashTransactionRepository = cashTransactionRepository;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** The user's state as seen under the lock, gathered once per operation. */
    private record Locked(User user, RiskSettings settings, List<Holding> holdings) {

        Holding holding(String symbol) {
            return holdings.stream()
                    .filter(h -> h.getSymbol().equals(symbol))
                    .findFirst()
                    .orElseGet(() -> Holding.empty(user.getId(), symbol));
        }

        BigDecimal otherHoldingsAtCost(String symbol) {
            return holdings.stream()
                    .filter(h -> !h.getSymbol().equals(symbol))
                    .map(h -> RiskService.orderValue(h.getAvgPrice(), h.getQuantity()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    /**
     * Not {@code @Transactional} itself, on purpose. The price comes from the cache or
     * Finnhub, and a network call must not happen while the user's row is locked. So:
     * fetch the price, then open the transaction that locks, checks and executes.
     *
     * <p>The rejection is thrown here, after the transaction has committed. Thrown from
     * inside it, the REJECTED order row would roll back with everything else and rule 4
     * (rejected orders stay in the audit trail) would quietly fail.
     */
    public OrderResponse placeOrder(Long userId, PlaceOrderRequest request) {
        validate(request);
        String symbol = MarketService.normalizeSymbol(request.symbol());
        // Also proves the symbol exists, for pending orders that would otherwise never be priced.
        BigDecimal price = marketService.currentPrice(symbol);

        Order order = transaction.execute(status -> {
            Locked locked = lock(userId);
            Order created = orderRepository.save(newOrder(userId, symbol, request));
            Optional<String> rejection = risk(locked, created, created.referencePrice(price), created.getId());
            if (rejection.isPresent()) {
                created.reject(rejection.get());
            } else if (created.getType() == OrderType.MARKET) {
                settle(locked, created, price);
            }
            // A limit or stop-loss that passed risk simply stays PENDING for the scheduler.
            return created;
        });

        if (order.getStatus() == OrderStatus.REJECTED) {
            throw new OrderRejectedException(order.getRejectReason());
        }
        return OrderResponse.from(order);
    }

    /**
     * Fills a pending order at {@code price}, called by the scheduler once the price has
     * triggered it. Re-runs the risk rules at the fill price against the user's state
     * now, since cash and shares may have moved since the order was placed; an order
     * that no longer passes is rejected rather than filled. Does nothing if the order
     * has been cancelled or filled in the meantime.
     *
     * <p>{@code userId} comes from the caller because the lock must be the first read.
     */
    public void fillPending(Long userId, Long orderId, BigDecimal price) {
        transaction.executeWithoutResult(status -> {
            Locked locked = lock(userId);
            Order order = orderRepository.findById(orderId).orElse(null);
            if (order == null || !order.getUserId().equals(userId) || order.getStatus() != OrderStatus.PENDING) {
                return; // cancelled or already filled while the scheduler was looking at it
            }
            if (!order.isTriggeredAt(price)) {
                return;
            }
            Optional<String> rejection = risk(locked, order, price, order.getId());
            if (rejection.isPresent()) {
                order.reject(rejection.get());
                log.info("Pending order {} rejected at fill: {}", order.getId(), rejection.get());
            } else {
                settle(locked, order, price);
            }
        });
    }

    /** Cancels one of the caller's own pending orders. */
    public OrderResponse cancel(Long userId, Long orderId) {
        Order order = transaction.execute(status -> {
            lock(userId);
            Order found = orderRepository.findById(orderId)
                    // Another user's order is reported as missing, not as forbidden.
                    .filter(o -> o.getUserId().equals(userId))
                    .orElseThrow(() -> new NotFoundException("Order not found"));
            if (found.getStatus() != OrderStatus.PENDING) {
                throw new OrderNotPendingException(found.getStatus().name());
            }
            found.cancel();
            return found;
        });
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> orders(Long userId) {
        return orderRepository.findByUserIdOrderByIdDesc(userId).stream().map(OrderResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<TradeResponse> trades(Long userId) {
        return tradeRepository.findByUserIdOrderByIdDesc(userId).stream().map(TradeResponse::from).toList();
    }

    // ---- internals ----

    /** Rule 3: the first statement of every write. */
    private Locked lock(Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        RiskSettings settings = riskSettingsRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("No risk settings for user " + userId));
        return new Locked(user, settings, holdingRepository.findByUserId(userId));
    }

    /**
     * Shares already promised to other pending sells are not available to this order,
     * otherwise a stop-loss and a limit sell could both claim the same shares. The
     * order being judged is excluded: it is already saved as PENDING, and counting it
     * would make it compete with itself.
     */
    private Optional<String> risk(Locked locked, Order order, BigDecimal price, long excludeOrderId) {
        Holding holding = locked.holding(order.getSymbol());
        long available = holding.getQuantity();
        if (order.getSide() == OrderSide.SELL) {
            available -= orderRepository.pendingSellQuantity(order.getUserId(), order.getSymbol(), excludeOrderId);
        }
        return riskService.reject(
                new RiskCheck(
                        order.getSide(),
                        order.getQuantity(),
                        price,
                        locked.user().getCashBalance(),
                        Math.max(available, 0),
                        locked.otherHoldingsAtCost(order.getSymbol())),
                locked.settings());
    }

    /** Rule 2: the order, trade, cash, ledger and holding change together, in the caller's transaction. */
    private void settle(Locked locked, Order order, BigDecimal price) {
        User user = locked.user();
        long quantity = order.getQuantity();
        Holding holding = locked.holding(order.getSymbol());

        BigDecimal value = RiskService.orderValue(price, quantity);
        BigDecimal signed;
        if (order.getSide() == OrderSide.BUY) {
            holding.buy(quantity, price);
            signed = value.negate();
        } else {
            holding.sell(quantity, price);
            signed = value;
        }
        user.setCashBalance(user.getCashBalance().add(signed));

        holdingRepository.save(holding);
        tradeRepository.save(new Trade(order, price));
        cashTransactionRepository.save(
                new CashTransaction(user.getId(), order.getId(), order.getSide(), signed, user.getCashBalance()));
        order.fill();

        if (order.getSide() == OrderSide.BUY && order.isAttachStopLoss()) {
            BigDecimal trigger = price
                    .multiply(HUNDRED.subtract(locked.settings().getDefaultStopLossPct()))
                    .divide(HUNDRED, 4, RoundingMode.HALF_UP);
            orderRepository.save(Order.stopLoss(user.getId(), order.getSymbol(), quantity, trigger));
        }
    }

    private static Order newOrder(Long userId, String symbol, PlaceOrderRequest request) {
        boolean attach = Boolean.TRUE.equals(request.attachStopLoss());
        return switch (request.type()) {
            case MARKET -> Order.market(userId, symbol, request.side(), request.quantity(), attach);
            case LIMIT -> Order.limit(userId, symbol, request.side(), request.quantity(), request.limitPrice(), attach);
            case STOP_LOSS -> Order.stopLoss(userId, symbol, request.quantity(), request.triggerPrice());
        };
    }

    /** Which price fields belong with which type; the annotations cannot express this. */
    private static void validate(PlaceOrderRequest request) {
        switch (request.type()) {
            case MARKET -> {
                if (request.limitPrice() != null || request.triggerPrice() != null) {
                    throw new InvalidOrderException("A market order takes no limit or trigger price");
                }
            }
            case LIMIT -> {
                if (request.limitPrice() == null) {
                    throw new InvalidOrderException("A limit order needs a limitPrice");
                }
                if (request.triggerPrice() != null) {
                    throw new InvalidOrderException("A limit order takes no triggerPrice");
                }
            }
            case STOP_LOSS -> {
                if (request.side() != OrderSide.SELL) {
                    throw new InvalidOrderException("A stop-loss order can only sell");
                }
                if (request.triggerPrice() == null) {
                    throw new InvalidOrderException("A stop-loss order needs a triggerPrice");
                }
                if (request.limitPrice() != null) {
                    throw new InvalidOrderException("A stop-loss order takes no limitPrice");
                }
                if (Boolean.TRUE.equals(request.attachStopLoss())) {
                    throw new InvalidOrderException("A stop-loss order cannot attach another stop-loss");
                }
            }
        }
        if (request.side() == OrderSide.SELL && Boolean.TRUE.equals(request.attachStopLoss())) {
            throw new InvalidOrderException("Only a buy can attach a stop-loss");
        }
    }
}
