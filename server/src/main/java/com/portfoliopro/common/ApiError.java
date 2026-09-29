package com.portfoliopro.common;

import java.time.Instant;
import java.util.Map;

/**
 * The one error shape the API ever returns.
 *
 * @param fieldErrors present only for validation failures, keyed by field name
 */
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        Map<String, String> fieldErrors) {

    public static ApiError of(int status, String code, String message, String path) {
        return new ApiError(Instant.now(), status, code, message, path, null);
    }
}
