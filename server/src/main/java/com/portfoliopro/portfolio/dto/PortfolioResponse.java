package com.portfoliopro.portfolio.dto;

import java.util.List;

/** Money is a string throughout (rule 1). {@code realizedPnl} includes fully sold positions. */
public record PortfolioResponse(
        String cash,
        String holdingsValue,
        String totalValue,
        String unrealizedPnl,
        String realizedPnl,
        List<HoldingResponse> holdings) {
}
