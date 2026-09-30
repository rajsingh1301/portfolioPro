package com.portfoliopro.trading;

import com.portfoliopro.auth.AuthPrincipal;
import com.portfoliopro.trading.dto.PerformanceResponse;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PerformanceController {

    private final PerformanceService performanceService;

    public PerformanceController(PerformanceService performanceService) {
        this.performanceService = performanceService;
    }

    /** Under /api/portfolio because that is what it describes, though it is built from trades. */
    @GetMapping("/api/portfolio/performance")
    public PerformanceResponse performance(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(defaultValue = "3M")
                    @Pattern(regexp = PerformanceRange.PATTERN, message = "must be one of 1M, 3M, 6M, 1Y") String range) {
        return performanceService.performance(principal.userId(), range);
    }
}
