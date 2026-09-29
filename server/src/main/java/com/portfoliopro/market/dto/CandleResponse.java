package com.portfoliopro.market.dto;

/**
 * {@code time} is epoch seconds in UTC, which is what the chart library takes.
 * Prices are strings like every other money value on the wire.
 */
public record CandleResponse(long time, String open, String high, String low, String close, long volume) {
}
