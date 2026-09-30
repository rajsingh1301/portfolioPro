package com.portfoliopro.trading.dto;

import java.time.Instant;
import java.util.List;

/**
 * What the portfolio has made or lost since the previous close. Money is a string (rule 1).
 * {@code dayPnlPercent} is against what the portfolio was worth at the previous close.
 * A position whose quote could not be fetched is left out of the total and has no figures.
 */
public record TodayResponse(String dayPnl, String dayPnlPercent, List<Position> positions, Instant asOf) {

    /** {@code dayChange} is per share, against the previous close; {@code dayPnl} is for the whole position. */
    public record Position(
            String symbol, long quantity, String previousClose, String dayChange, String dayChangePercent, String dayPnl) {
    }
}
