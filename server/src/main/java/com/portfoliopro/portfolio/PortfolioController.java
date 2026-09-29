package com.portfoliopro.portfolio;

import com.portfoliopro.auth.AuthPrincipal;
import com.portfoliopro.portfolio.dto.AllocationSlice;
import com.portfoliopro.portfolio.dto.PortfolioResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/portfolio")
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping
    public PortfolioResponse portfolio(@AuthenticationPrincipal AuthPrincipal principal) {
        return portfolioService.portfolio(principal.userId());
    }

    @GetMapping("/allocation")
    public List<AllocationSlice> allocation(@AuthenticationPrincipal AuthPrincipal principal) {
        return portfolioService.allocation(principal.userId());
    }
}
