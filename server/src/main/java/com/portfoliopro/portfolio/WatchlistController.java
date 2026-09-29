package com.portfoliopro.portfolio;

import com.portfoliopro.auth.AuthPrincipal;
import com.portfoliopro.portfolio.dto.AddWatchlistRequest;
import com.portfoliopro.portfolio.dto.WatchlistItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/watchlist")
public class WatchlistController {

    private final WatchlistService watchlistService;

    public WatchlistController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    @GetMapping
    public List<WatchlistItem> list(@AuthenticationPrincipal AuthPrincipal principal) {
        return watchlistService.list(principal.userId());
    }

    /** {@code 201} when the symbol was added, {@code 200} when it was already followed. */
    @PostMapping
    public ResponseEntity<WatchlistItem> add(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody AddWatchlistRequest request) {
        WatchlistService.AddResult result = watchlistService.add(principal.userId(), request.symbol());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.item());
    }

    @DeleteMapping("/{symbol}")
    public ResponseEntity<Void> remove(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Pattern(regexp = "[A-Za-z0-9.\\-]{1,20}", message = "must be a ticker symbol") String symbol) {
        watchlistService.remove(principal.userId(), symbol);
        return ResponseEntity.noContent().build();
    }
}
