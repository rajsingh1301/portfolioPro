package com.portfoliopro.common.exception;

import org.springframework.http.HttpStatus;

/**
 * A well-formed order that failed a risk rule. Thrown only after the REJECTED order
 * row has been committed, so the audit trail survives the failure response.
 */
public class OrderRejectedException extends ApiException {

    public OrderRejectedException(String reason) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "ORDER_REJECTED", "Order rejected: " + reason);
    }
}
