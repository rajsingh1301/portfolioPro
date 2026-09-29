package com.portfoliopro.common.exception;

import org.springframework.http.HttpStatus;

/** Only a pending order can be cancelled; a filled, rejected or cancelled one is settled. */
public class OrderNotPendingException extends ApiException {

    public OrderNotPendingException(String status) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "ORDER_NOT_PENDING",
                "Only a pending order can be cancelled; this one is " + status.toLowerCase());
    }
}
