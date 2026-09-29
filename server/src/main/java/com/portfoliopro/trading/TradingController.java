package com.portfoliopro.trading;

import com.portfoliopro.auth.AuthPrincipal;
import com.portfoliopro.trading.dto.OrderResponse;
import com.portfoliopro.trading.dto.PlaceOrderRequest;
import com.portfoliopro.trading.dto.TradeResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class TradingController {

    private final TradingService tradingService;

    public TradingController(TradingService tradingService) {
        this.tradingService = tradingService;
    }

    @PostMapping("/api/orders")
    public ResponseEntity<OrderResponse> place(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody PlaceOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tradingService.placeOrder(principal.userId(), request));
    }

    @GetMapping("/api/orders")
    public List<OrderResponse> orders(@AuthenticationPrincipal AuthPrincipal principal) {
        return tradingService.orders(principal.userId());
    }

    @GetMapping("/api/trades")
    public List<TradeResponse> trades(@AuthenticationPrincipal AuthPrincipal principal) {
        return tradingService.trades(principal.userId());
    }
}
