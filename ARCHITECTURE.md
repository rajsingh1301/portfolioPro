# PortfolioPro — Architecture

Paper-trading web app. Users practise buying and selling real-market stocks with
virtual money, track a portfolio, and read technical/fundamental analysis. No real
funds are ever involved.

This document describes how the system is designed and why. It is the reference for
anyone (human or agent) writing code in this repo. For what currently exists and what
is still to do, see [PROGRESS.md](PROGRESS.md).

---

## 1. Locked decisions

| Decision | Choice | Reason |
|---|---|---|
| Frontend language | **TypeScript** | Money arrives as strings over JSON; types stop them being silently treated as numbers |
| Frontend stack | React 19 + Vite, Tailwind CSS, React Router, Axios | Fast dev loop; Axios interceptor injects the JWT once |
| Charts | TradingView Lightweight Charts | Free, purpose-built for candlesticks |
| Backend | Java 17+, Spring Boot (Maven) | `BigDecimal`, declarative transactions, mature security |
| Database | MySQL 8 (InnoDB) | Row-level locking + ACID, needed for the order transaction |
| Auth | Spring Security + JWT, BCrypt | Stateless; no server-side session store |
| Market data | **Finnhub** (quotes, search, fundamentals) + **Twelve Data** (candles) | Finnhub's candle endpoint is paid-only, so chart history comes from Twelve Data's free plan |
| Currency | **USD**, `$100,000` starting balance | Finnhub's free tier is US equities; see §7 |
| Indicators | ta4j (pinned to 0.22.6, the last release that runs on Java 21) | SMA/EMA/RSI/MACD/Bollinger without hand-rolling maths |
| Price cache | Spring Cache + Caffeine | Finnhub rate limit is ~60 calls/min; the cache is what makes the app survive it |
| Architecture | 3-tier modular monolith | One deployable, module boundaries kept clean inside it |

## 2. Shape of the system

```
React client  ──REST + JSON, Authorization: Bearer <jwt>──▶  Spring Boot backend
                                                                    │
                                    ┌───────────────────────────────┤
                                    ▼                               ▼
                             MySQL (JPA/Hibernate)          Finnhub API
                                                            (via Caffeine cache)
```

Layers, top to bottom:

| Layer | Component | Responsibility |
|---|---|---|
| Presentation | React client | Pages, charts, forms |
| Security | Spring Security + JWT filter | Authenticates every request except signup/login |
| Application | Controllers → Services | Validate input, apply business rules, own transactions |
| Data access | Spring Data JPA | Repositories map entities to tables |
| Cache | Caffeine | Latest quote per symbol, 10–30s TTL |
| Data | MySQL (InnoDB) | Users, orders, trades, holdings, cash ledger |

## 3. Backend modules

Package root: `com.portfoliopro`. Every module follows the same shape —
**Controller** (REST, DTOs only) → **Service** (business rules, `@Transactional`) →
**Repository** (data access). Entities never leave the service layer; controllers
speak DTOs.

| Module | Owns |
|---|---|
| `auth` | Signup, login, JWT issue/verify, current user |
| `market` | Stock search, quotes, candles, the Finnhub and Twelve Data clients, caches |
| `trading` | Orders, trades, execution engine, the pending-order scheduler |
| `portfolio` | Holdings, P&L, allocation, watchlist |
| `risk` | Pre-trade checks and per-user risk settings |
| `analysis` | Indicator series (ta4j), fundamentals |
| `common` | Exceptions, shared DTOs, the global error handler, utilities |
| `config` | Security, CORS, cache, scheduling configuration |

**Dependency rule:** `trading` may call `risk`, `market` and `portfolio`. Nothing
calls `trading`. `common` and `config` are called by everyone and call nobody.

## 4. Frontend structure

```
client/src/
  pages/        Login, Signup, Dashboard, Stock, Orders, Watchlist
  components/   Chart, OrderForm, HoldingsTable, IndicatorPanel, ...
  api/          Axios instance + one typed module per backend module
  context/      AuthContext (token, current user)
  hooks/        useQuote, usePortfolio, ...
  types/        Shared response/request types mirroring backend DTOs
```

Routing is React Router. A `<ProtectedRoute>` wrapper redirects to `/login` when no
token is present. The Axios interceptor attaches the JWT and, on a `401`, clears the
token and bounces to login.

## 5. Data model

| Table | Purpose | Key columns |
|---|---|---|
| `users` | Account and virtual cash | `id`, `email` (unique), `password_hash`, `cash_balance` |
| `stocks` | Tradable symbols | `symbol` (PK), `name`, `exchange`, `sector` |
| `orders` | Every buy/sell request | `id`, `user_id`, `symbol`, `side`, `type`, `quantity`, `limit_price`, `trigger_price`, `status`, `reject_reason` |
| `trades` | Actual executions | `id`, `order_id`, `symbol`, `side`, `quantity`, `price`, `executed_at` |
| `holdings` | Current portfolio | `(user_id, symbol)` PK, `quantity`, `avg_price`, `realized_pnl` |
| `cash_transactions` | Cash ledger; its amounts sum to the balance | `id`, `user_id`, `order_id`, `type` (`DEPOSIT`, `BUY`, `SELL`), `amount`, `balance_after` |
| `risk_settings` | Per-user limits | `user_id` (PK), `max_position_pct`, `max_order_value`, `default_stop_loss_pct` |
| `watchlist` | Stocks a user follows | `(user_id, symbol)` PK |
| `price_candles` | Chart history (**never created**: cached in memory instead) | `(symbol, interval, ts)` PK, OHLCV |
| `stock_fundamentals` | Latest ratios (**never created**: cached in memory instead) | `symbol` (PK), `market_cap`, `pe_ratio`, `eps`, `roe`, `dividend_yield` |

Relationships: a user has many orders, trades, holdings, ledger rows and watchlist
entries; an order has many trades (partial fills); a user has exactly one
`risk_settings` row.

## 6. Non-negotiable rules

These hold everywhere. A change that breaks one of them is a bug, not a trade-off.

1. **Money is `BigDecimal` in Java and `DECIMAL` in MySQL.** Never `double`, never
   `float`, at any layer including DTOs and JSON. Over the wire money is a string.
2. **An order executes in one transaction or not at all.** Insert order, insert trade,
   move cash, write the ledger row, update the holding — all inside one
   `@Transactional` method. A partial trade must be impossible.
3. **Lock the user row before touching cash.** `SELECT ... FOR UPDATE` on `users`, so
   two concurrent orders cannot spend the same balance.
4. **Risk runs before execution, never after.** A rejected order is still persisted,
   with `status = REJECTED` and a reason — the audit trail keeps failures.
5. **Every query filters by the authenticated user id.** No endpoint may return
   another user's data, even by accident.
6. **Secrets live in environment variables.** JWT signing key, Finnhub API key, DB
   password. Nothing secret enters git.
7. **Timestamps are stored in UTC.**
8. **The app never says "buy" or "sell".** Indicator labels stay neutral
   ("oversold zone", "bearish cross"). Indicators are frequently wrong.

## 7. Market data and the cache

Finnhub free tier is roughly **60 calls/minute** and covers **US equities**. That
limit, not the database, is the real constraint on the app.

Quote path: client asks for a quote → market service checks Caffeine → a value
younger than the TTL (10–30s) is returned immediately → otherwise call Finnhub, cache
the result, return it. Ten users watching `AAPL` cost one upstream call, not ten.

The pending-order scheduler reads the **same cache**, so it never adds upstream load.

*Consequence of choosing Finnhub:* prices are USD and symbols are US tickers, so the
starting balance is `$100,000`. Indian equities would need a different data source.

## 8. Order execution

**Market buy** — validate → read cached price → run risk checks (reject with reason
and `422` on failure) → open transaction, lock the user row → insert order + trade,
debit cash, write ledger, upsert holding → commit, return `201`.

**Market sell** — same, plus: check quantity held, reduce the holding, credit cash,
and book realized P&L as `(sell price − avg buy price) × quantity`.

**Limit / stop-loss** — saved as `PENDING` after the risk check. A scheduled job runs
every 10–15s, compares pending orders against the cached price, and fills through the
*same* transactional path as a market order. A buy limit fills at or below its limit;
a stop-loss sells when price falls to the trigger.

Order states: `PENDING`, `FILLED`, `PARTIAL`, `CANCELLED`, `REJECTED`.

## 9. Risk rules

| Rule | Applies to | Default | On failure |
|---|---|---|---|
| Sufficient balance | Buy | Order value ≤ cash | `REJECTED: insufficient balance` |
| Sufficient quantity | Sell | Quantity ≤ holding | `REJECTED: not enough shares` |
| Max position size | Buy | 20% of portfolio in one stock | `REJECTED: position limit` |
| Max order value | Buy, sell | `$5,000` per order | `REJECTED: order too large` |
| Default stop-loss | Buy (optional) | 5% below fill price | Stop-loss order created automatically |

Limits live in `risk_settings` and are adjustable per user within bounds.

## 10. API surface

All routes under `/api`. All require `Authorization: Bearer <token>` except signup and
login.

| Module | Method | Endpoint |
|---|---|---|
| Auth | POST | `/api/auth/signup` |
| Auth | POST | `/api/auth/login` |
| Auth | GET | `/api/auth/me` |
| Market | GET | `/api/stocks/search?q=` |
| Market | GET | `/api/stocks/{symbol}/quote` |
| Market | GET | `/api/stocks/{symbol}/candles` |
| Trading | POST | `/api/orders` |
| Trading | GET | `/api/orders` |
| Trading | DELETE | `/api/orders/{id}` |
| Trading | GET | `/api/trades` |
| Portfolio | GET | `/api/portfolio` |
| Portfolio | GET | `/api/portfolio/allocation` |
| Watchlist | GET / POST | `/api/watchlist` |
| Watchlist | DELETE | `/api/watchlist/{symbol}` |
| Risk | GET / PUT | `/api/risk/settings` |
| Analysis | GET | `/api/stocks/{symbol}/indicators` |
| Analysis | GET | `/api/stocks/{symbol}/fundamentals` |

Conventions: `200` read, `201` created, `400` bad input, `401` unauthenticated,
`404` not found, `422` business rule failed, `503` upstream market data unavailable. One JSON error shape from a global
exception handler. Bean Validation on every request body. CORS allows only
`http://localhost:5173` and the deployed frontend origin.

## 11. Security

- BCrypt password hashing; plaintext passwords are never stored or logged.
- Signed JWT, short expiry, signing key from the environment.
- Every query scoped to the authenticated user id.
- Bean Validation on all input; JPA parameter binding (no string-concatenated SQL).
- Row lock on `users` so parallel orders cannot double-spend.
- HTTPS in production; CORS restricted to known origins.

## 12. Build order

Each slice is built and verified end-to-end (backend + UI) before the next begins.

1. **Auth** — signup, login, JWT, protected route, `$100,000` on signup
2. **Market data** — Finnhub client, quote endpoint, Caffeine cache, search
3. **Trading (market orders only)** — the atomic transaction, risk checks, order list
4. **Portfolio** — holdings, unrealized/realized P&L, dashboard
5. **Charts** — candles endpoint + Lightweight Charts
6. **Pending orders** — limit, stop-loss, the scheduler
7. **Watchlist**
8. **Analysis** — ta4j indicators, fundamentals card
9. **Risk settings UI**

All nine slices are built. `price_candles` and `stock_fundamentals` were never created: candles and
fundamentals are cached in memory rather than stored, and nothing needs more history than the
cache holds (see PROGRESS.md).

Slice 3 is the hard one; slices 1–2 exist mainly to make it testable.
