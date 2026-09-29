package com.portfoliopro.market.dto;

/**
 * Finnhub's search payload carries no exchange or sector — those need a separate
 * company-profile call, so {@code stocks.exchange} stays null until something makes
 * one. Nothing is returned here that the provider did not actually give us.
 */
public record StockSearchResult(String symbol, String name) {
}
