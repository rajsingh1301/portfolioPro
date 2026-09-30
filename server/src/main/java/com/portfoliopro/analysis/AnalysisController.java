package com.portfoliopro.analysis;

import com.portfoliopro.analysis.dto.FundamentalsResponse;
import com.portfoliopro.analysis.dto.IndicatorResponse;
import com.portfoliopro.market.CandleRange;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stocks/{symbol}")
public class AnalysisController {

    private static final String TICKER = "[A-Za-z0-9.\\-]{1,20}";

    private final IndicatorService indicatorService;
    private final FundamentalsService fundamentalsService;

    public AnalysisController(IndicatorService indicatorService, FundamentalsService fundamentalsService) {
        this.indicatorService = indicatorService;
        this.fundamentalsService = fundamentalsService;
    }

    @GetMapping("/indicators")
    public IndicatorResponse indicators(
            @PathVariable @Pattern(regexp = TICKER, message = "must be a ticker symbol") String symbol,
            @RequestParam(defaultValue = "1Y")
                    @Pattern(regexp = CandleRange.PATTERN, message = "must be one of 1D, 1W, 1M, 6M, 1Y, 5Y") String range) {
        return indicatorService.indicators(symbol, range);
    }

    @GetMapping("/fundamentals")
    public FundamentalsResponse fundamentals(
            @PathVariable @Pattern(regexp = TICKER, message = "must be a ticker symbol") String symbol) {
        return fundamentalsService.fundamentals(symbol);
    }
}
