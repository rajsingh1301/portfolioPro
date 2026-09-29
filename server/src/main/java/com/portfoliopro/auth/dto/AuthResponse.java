package com.portfoliopro.auth.dto;

public record AuthResponse(String token, String tokenType, long expiresInMinutes, UserResponse user) {

    public static AuthResponse of(String token, long expiresInMinutes, UserResponse user) {
        return new AuthResponse(token, "Bearer", expiresInMinutes, user);
    }
}
