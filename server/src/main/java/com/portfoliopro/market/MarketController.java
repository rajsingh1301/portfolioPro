package com.portfoliopro.market;

import com.portfoliopro.market.dto.QuoteResponse;
import com.portfoliopro.market.dto.StockSearchResult;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Both routes require a token — the security chain authenticates everything that is
 * not signup or login, and an unauthenticated visitor must not be able to spend the
 * app's Finnhub rate limit.
 *
 * <p>No {@code @Validated} on the class: since Spring 6.1 MVC validates constrained
 * method parameters itself, and the annotation would replace that with an AOP proxy
 * throwing a raw ConstraintViolationException the handler would report as a 500.
 */
@RestController
@RequestMapping("/api/stocks")
public class MarketController {

    private final MarketService marketService;

    public MarketController(MarketService marketService) {
        this.marketService = marketService;
    }

    @GetMapping("/search")
    public List<StockSearchResult> search(
            @RequestParam @NotBlank @Size(min = 1, max = 50) String q) {
        // Named `q` rather than `query` so a constraint failure and a missing
        // parameter report the same field name back to the client.
        return marketService.search(q);
    }

    @GetMapping("/{symbol}/quote")
    public QuoteResponse quote(
            @PathVariable @Pattern(regexp = "[A-Za-z0-9.\\-]{1,20}", message = "must be a ticker symbol") String symbol) {
        return marketService.quote(symbol);
    }
}
