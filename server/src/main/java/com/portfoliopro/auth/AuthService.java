package com.portfoliopro.auth;

import com.portfoliopro.auth.dto.AuthResponse;
import com.portfoliopro.auth.dto.LoginRequest;
import com.portfoliopro.auth.dto.SignupRequest;
import com.portfoliopro.auth.dto.UserResponse;
import com.portfoliopro.common.exception.EmailAlreadyUsedException;
import com.portfoliopro.common.exception.InvalidCredentialsException;
import com.portfoliopro.common.exception.NotFoundException;
import com.portfoliopro.risk.RiskSettings;
import com.portfoliopro.risk.RiskSettingsRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;

@Service
public class AuthService {

    /** Every new account starts with this much virtual cash. */
    public static final BigDecimal STARTING_CASH = new BigDecimal("100000.0000");

    private final UserRepository userRepository;
    private final RiskSettingsRepository riskSettingsRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            RiskSettingsRepository riskSettingsRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.riskSettingsRepository = riskSettingsRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * Creates the account, its opening balance and its risk settings in one
     * transaction, so a user can never exist without the limits slice 3 expects.
     */
    @Transactional
    public AuthResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyUsedException();
        }

        User user = new User(email, passwordEncoder.encode(request.password()), STARTING_CASH);
        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            // Two signups for the same email raced past the check above; the unique
            // index is the authority, so report the same business failure.
            throw new EmailAlreadyUsedException();
        }
        riskSettingsRepository.save(RiskSettings.defaultsFor(user.getId()));

        return AuthResponse.of(jwtService.issue(user), jwtService.getExpiryMinutes(), UserResponse.from(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(normalizeEmail(request.email()))
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return AuthResponse.of(jwtService.issue(user), jwtService.getExpiryMinutes(), UserResponse.from(user));
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(Long userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                // A valid token for a deleted account: authenticated, but nothing to return.
                .orElseThrow(() -> new NotFoundException("User no longer exists"));
    }

    /** Emails are matched case-insensitively, so they are stored in one canonical form. */
    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
