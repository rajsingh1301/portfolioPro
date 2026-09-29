package com.portfoliopro.portfolio.dto;

/** {@code symbol} is the ticker, or {@code null} for the cash slice. */
public record AllocationSlice(String symbol, String label, String value, String percent) {
}
