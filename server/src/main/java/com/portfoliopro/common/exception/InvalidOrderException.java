package com.portfoliopro.common.exception;

import org.springframework.http.HttpStatus;

/** A request that is well-formed JSON but describes an order that cannot exist. */
public class InvalidOrderException extends ApiException {

    public InvalidOrderException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_ORDER", message);
    }
}
