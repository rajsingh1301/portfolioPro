package com.portfoliopro.risk;

import com.portfoliopro.auth.UserRepository;
import com.portfoliopro.common.exception.NotFoundException;
import com.portfoliopro.risk.dto.RiskSettingsResponse;
import com.portfoliopro.risk.dto.UpdateRiskSettingsRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RiskSettingsService {

    private final RiskSettingsRepository riskSettingsRepository;
    private final UserRepository userRepository;

    public RiskSettingsService(RiskSettingsRepository riskSettingsRepository, UserRepository userRepository) {
        this.riskSettingsRepository = riskSettingsRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public RiskSettingsResponse get(Long userId) {
        return RiskSettingsResponse.from(find(userId));
    }

    /**
     * Locks the user row first, like every other write. Any order in flight holds that
     * lock, so this waits for it to finish: once the update returns, no order is still
     * being checked under the old limits, which is what someone tightening a limit
     * expects. (A single-row read would never be torn either way; the lock is for that
     * ordering guarantee.)
     */
    @Transactional
    public RiskSettingsResponse update(Long userId, UpdateRiskSettingsRequest request) {
        userRepository.findByIdForUpdate(userId).orElseThrow(() -> new NotFoundException("User not found"));
        RiskSettings settings = find(userId);
        settings.setMaxPositionPct(request.maxPositionPct());
        settings.setMaxOrderValue(request.maxOrderValue());
        settings.setDefaultStopLossPct(request.defaultStopLossPct());
        return RiskSettingsResponse.from(riskSettingsRepository.save(settings));
    }

    private RiskSettings find(Long userId) {
        return riskSettingsRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Risk settings not found"));
    }
}
