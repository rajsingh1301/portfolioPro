package com.portfoliopro.portfolio.dto;

import java.util.List;

/**
 * Money is a string throughout (rule 1). {@code realizedPnl} includes fully sold positions.
 *
 * <p>{@code overallPnl} is everything made or lost since the account opened (unrealized plus
 * realized), and {@code overallPnlPercent} is that as a share of {@code netDeposits}, the money
 * put in, read from the ledger rather than assumed.
 */
public record PortfolioResponse(
        String cash,
        String holdingsValue,
        String totalValue,
        String unrealizedPnl,
        String realizedPnl,
        String netDeposits,
        String overallPnl,
        String overallPnlPercent,
        List<HoldingResponse> holdings) {
}
