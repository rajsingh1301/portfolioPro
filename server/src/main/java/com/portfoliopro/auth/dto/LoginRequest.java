package com.portfoliopro.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Email is required")
        String email,

        @NotBlank(message = "Password is required")
        String password) {

    /** Trimmed the same way as at signup, so the two always agree on what an email is. */
    public LoginRequest {
        email = email == null ? null : email.trim();
    }
}
