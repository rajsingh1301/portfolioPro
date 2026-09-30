package com.portfoliopro.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param password BCrypt silently truncates past 72 bytes, so that is the real ceiling
 */
public record SignupRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        @Size(max = 254, message = "Email must be at most 254 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        String password) {

    /**
     * The email is trimmed as the request is built, so validation sees what will be
     * stored. Otherwise {@code @Email} rejects a padded address that login would accept.
     * The password is never touched: whitespace in it is part of it.
     */
    public SignupRequest {
        email = email == null ? null : email.trim();
    }
}
