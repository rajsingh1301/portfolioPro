package com.portfoliopro.portfolio.dto;

/**
 * One followed symbol. {@code name} is absent for a symbol the app has never seen in a
 * search; {@code price}, {@code change} and {@code percentChange} are absent when the
 * quote could not be fetched, so one bad quote never hides the rest of the list.
 */
public record WatchlistItem(String symbol, String name, String price, String change, String percentChange) {
}
