package com.portfoliopro.common.exception;

import org.springframework.http.HttpStatus;

/** A business rule, not bad input: the request was well formed, the email is taken. */
public class EmailAlreadyUsedException extends ApiException {

    public EmailAlreadyUsedException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "EMAIL_ALREADY_USED", "That email is already registered");
    }
}
