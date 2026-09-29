package com.portfoliopro.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base for every failure the API reports deliberately. Carries the status and the
 * stable machine-readable code so the handler never has to guess either.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
