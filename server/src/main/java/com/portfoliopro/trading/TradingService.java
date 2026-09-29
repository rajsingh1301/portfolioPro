package com.portfoliopro.trading;

import com.portfoliopro.common.OrderSide;
import com.portfoliopro.auth.User;
import com.portfoliopro.auth.UserRepository;
import com.portfoliopro.common.exception.NotFoundException;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;

@Service
public class TradingService {

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
        String symbol = MarketService.normalizeSymbol(request.symbol());
        BigDecimal price = marketService.currentPrice(symbol);

        Order order = transaction.execute(status -> execute(userId, symbol, request, price));

        if (order.getStatus() == OrderStatus.REJECTED) {
            throw new OrderRejectedException(order.getRejectReason());
        }
        return OrderResponse.from(order);
    }

    /** Rules 2 and 3: one transaction, user row locked before cash is read. */
    private Order execute(Long userId, String symbol, PlaceOrderRequest request, BigDecimal price) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        RiskSettings settings = riskSettingsRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("No risk settings for user " + userId));

        long quantity = request.quantity();
        List<Holding> holdings = holdingRepository.findByUserId(userId);
        Holding holding = holdings.stream()
                .filter(h -> h.getSymbol().equals(symbol))
                .findFirst()
                .orElseGet(() -> Holding.empty(userId, symbol));
        BigDecimal otherAtCost = holdings.stream()
                .filter(h -> !h.getSymbol().equals(symbol))
                .map(h -> RiskService.orderValue(h.getAvgPrice(), h.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = orderRepository.save(new Order(userId, symbol, request.side(), request.type(), quantity));

        var rejection = riskService.reject(
                new RiskCheck(request.side(), quantity, price, user.getCashBalance(), holding.getQuantity(), otherAtCost),
                settings);
        if (rejection.isPresent()) {
            order.reject(rejection.get());
            return order;
        }

        BigDecimal value = RiskService.orderValue(price, quantity);
        BigDecimal signed;
        if (request.side() == OrderSide.BUY) {
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
                new CashTransaction(userId, order.getId(), request.side(), signed, user.getCashBalance()));
        order.fill();
        return order;
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> orders(Long userId) {
        return orderRepository.findByUserIdOrderByIdDesc(userId).stream().map(OrderResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<TradeResponse> trades(Long userId) {
        return tradeRepository.findByUserIdOrderByIdDesc(userId).stream().map(TradeResponse::from).toList();
    }
}
