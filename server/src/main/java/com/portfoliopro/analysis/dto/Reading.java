package com.portfoliopro.analysis.dto;

/**
 * A plain-words description of where an indicator stands. Deliberately descriptive and
 * never advisory (rule 8): "overbought zone", not "sell".
 */
public record Reading(String indicator, String label) {
}
