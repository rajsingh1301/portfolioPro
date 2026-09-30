package com.portfoliopro.trading;

import com.portfoliopro.auth.AuthPrincipal;
import com.portfoliopro.trading.dto.TodayResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TodayController {

    private final TodayService todayService;

    public TodayController(TodayService todayService) {
        this.todayService = todayService;
    }

    /** Under /api/portfolio because that is what it describes, though it needs the trades. */
    @GetMapping("/api/portfolio/today")
    public TodayResponse today(@AuthenticationPrincipal AuthPrincipal principal) {
        return todayService.today(principal.userId());
    }
}
