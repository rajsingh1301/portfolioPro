package com.portfoliopro.common.exception;

import org.springframework.http.HttpStatus;

/**
 * The upstream market data provider could not be reached, refused the call, or
 * returned something unusable. Distinct from a 500: nothing is wrong with this
 * service, and retrying later is reasonable.
 */
public class MarketDataUnavailableException extends ApiException {

    public MarketDataUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "MARKET_DATA_UNAVAILABLE", message);
    }
}
