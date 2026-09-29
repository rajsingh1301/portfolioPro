package com.portfoliopro.auth;

/**
 * What the JWT filter puts in the security context. Carries the user id so every
 * query downstream can be scoped to the authenticated user without a lookup.
 */
public record AuthPrincipal(Long userId, String email) {
}
