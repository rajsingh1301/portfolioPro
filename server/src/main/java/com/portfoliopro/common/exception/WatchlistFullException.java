package com.portfoliopro.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Every followed symbol can cost a Finnhub call each time its quote expires, and the
 * free tier's limit is shared by every user, so a watchlist has a ceiling.
 */
public class WatchlistFullException extends ApiException {

    public WatchlistFullException(int limit) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "WATCHLIST_FULL", "A watchlist holds at most " + limit + " symbols");
    }
}
