package com.portfoliopro.analysis;

import com.portfoliopro.analysis.dto.FundamentalsResponse;
import com.portfoliopro.common.exception.NotFoundException;
import com.portfoliopro.market.FinnhubClient;
import com.portfoliopro.market.Fundamentals;
import com.portfoliopro.market.MarketService;
import com.portfoliopro.market.Stock;
import com.portfoliopro.market.StockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class FundamentalsService {

    private final FinnhubClient finnhubClient;
    private final StockRepository stockRepository;
    private final TransactionTemplate transaction;

    public FundamentalsService(
            FinnhubClient finnhubClient, StockRepository stockRepository, PlatformTransactionManager transactionManager) {
        this.finnhubClient = finnhubClient;
        this.stockRepository = stockRepository;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public FundamentalsResponse fundamentals(String rawSymbol) {
        String symbol = MarketService.normalizeSymbol(rawSymbol);
        Fundamentals fundamentals = finnhubClient.fundamentals(symbol);
        if (fundamentals.isEmpty()) {
            throw new NotFoundException("No fundamentals for symbol: " + symbol);
        }
        remember(symbol, fundamentals);
        return new FundamentalsResponse(
                symbol,
                fundamentals.name(),
                fundamentals.exchange(),
                fundamentals.industry(),
                scaled(fundamentals.marketCap(), 0),
                scaled(fundamentals.peRatio(), 2),
                scaled(fundamentals.eps(), 2),
                scaled(fundamentals.roe(), 2),
                scaled(fundamentals.dividendYield(), 2),
                scaled(fundamentals.week52High(), 2),
                scaled(fundamentals.week52Low(), 2),
                scaled(fundamentals.beta(), 2));
    }

    /**
     * Search never learns a symbol's exchange or industry, so they are recorded here
     * the first time the profile is seen. Only gaps are filled; nothing already known is
     * overwritten, and this is skipped entirely when there is nothing to add.
     */
    private void remember(String symbol, Fundamentals fundamentals) {
        if (fundamentals.name() == null) {
            return;
        }
        transaction.executeWithoutResult(status -> {
            Stock stock = stockRepository.findById(symbol).orElse(null);
            if (stock == null) {
                Stock created = new Stock(symbol, fundamentals.name(), fundamentals.exchange());
                created.setSector(fundamentals.industry());
                stockRepository.save(created);
            } else if (stock.getExchange() == null && stock.getSector() == null
                    && (fundamentals.exchange() != null || fundamentals.industry() != null)) {
                stock.setExchange(fundamentals.exchange());
                stock.setSector(fundamentals.industry());
                stockRepository.save(stock);
            }
        });
    }

    private static String scaled(BigDecimal value, int scale) {
        return value == null ? null : value.setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }
}
