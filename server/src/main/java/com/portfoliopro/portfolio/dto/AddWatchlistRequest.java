package com.portfoliopro.portfolio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AddWatchlistRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9.\\-]{1,20}", message = "must be a ticker symbol") String symbol) {
}
