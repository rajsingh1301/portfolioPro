package com.portfoliopro.auth.dto;

import com.portfoliopro.auth.User;

/**
 * @param cashBalance a string, not a number: money must not be exposed to a JSON
 *                    parser that would turn it into a float
 */
public record UserResponse(Long id, String email, String cashBalance) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getCashBalance().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
    }
}
