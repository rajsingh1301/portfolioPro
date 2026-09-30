package com.portfoliopro.risk;

import com.portfoliopro.auth.AuthPrincipal;
import com.portfoliopro.risk.dto.RiskSettingsResponse;
import com.portfoliopro.risk.dto.UpdateRiskSettingsRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/risk/settings")
public class RiskController {

    private final RiskSettingsService riskSettingsService;

    public RiskController(RiskSettingsService riskSettingsService) {
        this.riskSettingsService = riskSettingsService;
    }

    @GetMapping
    public RiskSettingsResponse get(@AuthenticationPrincipal AuthPrincipal principal) {
        return riskSettingsService.get(principal.userId());
    }

    @PutMapping
    public RiskSettingsResponse update(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody UpdateRiskSettingsRequest request) {
        return riskSettingsService.update(principal.userId(), request);
    }
}
