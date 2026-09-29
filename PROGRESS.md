# PortfolioPro — Progress

What exists in this repo right now, and what is next. Updated as each slice lands.
For the design and the reasoning behind it, see [ARCHITECTURE.md](ARCHITECTURE.md).

**Last updated:** 2026-09-29
**Current slice:** 2 — Market data + cache (backend done, frontend next)

---

## Status at a glance

| Slice | Feature | Status |
|---|---|---|
| 0 | Project setup | ✅ Done |
| 1 | Auth | ✅ Done |
| 2 | Market data + cache | ✅ Done (search box and quote live in the trade panel) |
| 3 | Trading (market orders) | ✅ Done |
| 4 | Portfolio | ✅ Done |
| 5 | Charts | ✅ Done (volume bars deferred) |
| 6 | Pending orders (limit, stop-loss) | ✅ Done |
| 7 | Watchlist | ⬜ Not started |
| 8 | Analysis (indicators, fundamentals) | ⬜ Not started |
| 9 | Risk settings UI | ⬜ Not started |

Legend: ✅ done · 🟡 partial · ⬜ not started

---

## Running the tests

```bash
cd server && ./mvnw test          # 22 tests, ~25s, needs Docker running
```

Tests run against a real **MySQL 8.4 in Testcontainers**, not H2: the migrations are
MySQL-specific (`ENGINE=InnoDB`) and slice 3 needs real row locking, neither of which
an in-memory database exercises. One container is shared by the whole run. The
`portfoliopro` MySQL user has no privileges outside its own schema, so a local test
database was not an option anyway.

Finnhub is never called from a test — `StubFinnhub` serves canned JSON on a random
local port and counts the requests it receives, which is how the cache assertions
prove upstream calls were actually prevented.

## Running it locally

```bash
brew services start mysql@8.4          # MySQL 8.4 on :3306
cd server && ./mvnw spring-boot:run    # API on :8080, Flyway migrates on startup
cd client && npm run dev               # UI on :5173
```

Secrets live in `server/.env` and `client/.env`, both git-ignored. Copy the
`.env.example` beside each if you need to recreate them. The database `portfoliopro`
and the user `portfoliopro@localhost` already exist locally.

---

## Slice 0 — Project setup ✅

- `client/` on Vite + React 19 + TypeScript, with Tailwind CSS v4, React Router 7,
  Axios and `lightweight-charts` installed. Starter page replaced by the app shell.
- `server/` on Spring Boot 4.1.1 (Maven wrapper, Java 21 target). Note that Boot 4
  ships **Jackson 3** (`tools.jackson.databind`), so `ObjectMapper` imports come from
  there — Jackson 2 is present only as a JJWT runtime dependency.
- MySQL 8.4 running as a brew service; `portfoliopro` database and app user created.
- Git initialised with a root `.gitignore` that excludes both `.env` files.

---

## Slice 1 — Auth ✅

**Goal:** a visitor can sign up, receive `$100,000` of virtual cash, log in, and land
on a protected dashboard that shows their email and balance. — met.

**Backend**

- [x] Spring Boot project in `server/` (Maven, Java 21)
- [x] Dependencies: Web MVC, Security, Data JPA, Validation, MySQL driver, Flyway, JJWT 0.13
- [x] MySQL connection from environment variables via `spring.config.import` of `.env`
- [x] `users` table + `User` entity (`cash_balance` as `DECIMAL(19,4)`)
- [x] `UserRepository`
- [x] BCrypt password encoder
- [x] JWT issue + verify (`JwtService`), HS512, signing key from the environment
- [x] `JwtAuthenticationFilter` on the security chain
- [x] Security config: signup and login public, everything else authenticated
- [x] CORS allowing `http://localhost:5173`
- [x] `POST /api/auth/signup` — creates the user with `$100,000` and a `risk_settings` row, returns a token
- [x] `POST /api/auth/login` — verifies credentials, returns a token
- [x] `GET /api/auth/me` — current user and cash balance
- [x] Global exception handler with the single JSON error shape
- [x] Bean Validation on both request bodies

**Frontend**

- [x] Tailwind, React Router, Axios installed and configured
- [x] Axios instance with the JWT request interceptor and 401 response handling
- [x] `AuthProvider` + `useAuth` holding token and current user
- [x] Signup page
- [x] Login page
- [x] `ProtectedRoute` wrapper
- [x] Dashboard placeholder showing email and cash balance
- [x] Logout

**Verified** by driving the API directly: signup returns `201` with
`"cashBalance": "100000.00"`; a duplicate email returns `422`; bad input returns `400`
with per-field messages; a wrong password returns `401`; login is case- and
whitespace-insensitive on email; `/me` returns `401` as JSON with no or a tampered
token and `200` with a good one; CORS preflight passes from `:5173` and is refused from
another origin; the database holds `100000.0000`, a BCrypt hash and one defaulted
`risk_settings` row, with UTC timestamps.

**Covered by tests** (`AuthApiTest`, 7 tests): the funded account and its risk-settings
row, email lowercasing, duplicate email as `422`, per-field validation, a wrong password
and an unknown email returning the same code so registered emails cannot be enumerated,
and `/me` against no token, a tampered token and a good one. One test documents an
asymmetry rather than a nicety: `SignupRequest` carries `@Email`, which rejects a
padded address before `AuthService` can trim it, while `LoginRequest` carries only
`@NotBlank` and accepts one.

**Not verified:** the rendered UI in a real browser — no browser was driven. `tsc` and
ESLint are clean and the production build succeeds, but the pages have not been
clicked through.

---

## Slice 2 — Market data + cache ✅

**Goal:** an authenticated user can search symbols and see a live quote, with Finnhub
called at most once per symbol per TTL.

**Backend**

- [x] Finnhub API key in `server/.env` as `FINNHUB_API_KEY`
- [x] `market` module: `FinnhubClient` (`RestClient`, 5s connect/read timeout), `MarketService`, `MarketController`
- [x] Caffeine cache: quotes 15s, symbol search 60 min, via `config/CacheConfig`
- [x] `stocks` table + migration `V2__market.sql`
- [x] `GET /api/stocks/search?q=` — top 10 US common stocks, upserts what it finds
- [x] `GET /api/stocks/{symbol}/quote` — price, change, %, high, low, open, previous close
- [x] `MarketDataUnavailableException` → `503` when Finnhub is unreachable or unreadable
- [x] Bean Validation on both parameters, reported through the existing error shape

**Frontend**

- [x] Typed client (`api/trading.ts`), symbol search box and quote, built into slice 3's trade panel

**Verified** by driving the API against live Finnhub and MySQL: `AAPL`, `MSFT`, `TSLA`
and `NVDA` return real prices as strings to two decimals; a lowercase path symbol is
normalised to uppercase; an unknown symbol returns `404` (Finnhub answers zeros, not an
error); both routes return `401` without a token and with a tampered one; a blank,
missing or over-long `q` and a non-ticker symbol all return `400` with the field named
`q` or `symbol`; CORS preflight from `:5173` passes. **The cache was measured, not
assumed:** the first `NVDA` quote took 1.17s, the next four 5–15ms, and a call 16s later
took 0.9s — the TTL expiring and going upstream again. A symbol Finnhub does not carry
is cached too (0.35s then 4ms), so a bad symbol cannot be used to burn the rate limit.
The `stocks` table holds the searched symbols with UTC timestamps, and re-running the
same search does not duplicate rows.

**Covered by tests** (`MarketApiTest`, 14 tests): the quote shape and string prices,
decimal precision past what a double holds, an unknown symbol as `404`, the cache
proven by counting stub requests (5 calls → 1 upstream, and 2 after the TTL expires),
an unknown symbol cached too, `503` on both an upstream error and an unreadable body,
search filtering and persistence, idempotent repeat searches, `401` before any upstream
call, and every parameter rejection.

**Not verified:** no UI exists for this slice yet. Real Finnhub rate-limiting (HTTP
`429`) is still unobserved — the stub covers a `500` and a malformed body, which take
the same code path, but the provider's actual throttling behaviour has not been seen.

## Slice 3 — Trading (market orders) ✅

**Goal:** an authenticated user can buy and sell at the current price, with risk checks
before execution and no way for concurrent orders to spend the same cash.

**Backend**

- [x] `V3__trading.sql`: `orders`, `trades`, `holdings`, `cash_transactions`
- [x] `POST /api/orders` (`201`, or `422` on a risk rejection), `GET /api/orders`, `GET /api/trades`
- [x] `TradingService`: price fetched *before* the transaction; then one transaction that locks the user row (`SELECT ... FOR UPDATE`), runs risk, inserts order + trade, moves cash, writes the ledger, updates the holding
- [x] `RiskService`: sufficient balance, sufficient shares, max order value, max position size. Pure: `TradingService` passes it the facts
- [x] A rejected order is committed as `REJECTED` with its reason, and `OrderRejectedException` is thrown only after the commit
- [x] Realized P&L and volume-weighted average cost on `Holding`

**Frontend**

- [x] `TradePanel` (search, quote, buy/sell, whole-share quantity) and `OrderHistory` on the dashboard; cash refreshes after every order

**Tests:** 38 in total (16 new: 7 unit tests on the risk rules, 9 API tests). The race test fires
eight $600 buys at $1,000 of cash and asserts exactly one fills. **It was checked to fail
without the lock:** with `LockModeType.NONE` all eight fill and none is rejected, so the
test really is exercising the lock.

**Verified** against real MySQL and real Finnhub: a `BUY 3 AAPL` at `338.40` filled and cash
went from `100000` to `98984.80`; a `SELL 99` was refused `422 not enough shares`.

**Decisions made building it**

- Quantity is whole shares (`BIGINT`).
- Position limit values the portfolio as cash + other holdings *at cost* + this symbol at the current price, so checking one order never costs a Finnhub call per holding.
- `orders.symbol` has no foreign key to `stocks`: that table only holds searched symbols, while any symbol Finnhub quotes is tradable.
- A fully sold position stays as a row with `quantity = 0`, so its realized P&L is not lost.
- `OrderSide` lives in `common`, because `risk` needs it and nothing may depend on `trading`.
- `DELETE /api/orders/{id}` and limit/trigger price columns are left for slice 6; nothing can be `PENDING` yet.
- No opening `DEPOSIT` ledger row is written at signup, so the ledger does not yet reconcile to the starting `$100,000`.

## Slice 4 — Portfolio ✅

**Goal:** a user can see what they hold, what it is worth now, and how much they have
made or lost.

**Backend**

- [x] `GET /api/portfolio`: cash, invested value, total value, unrealized and realized P&L, and each open holding with price, market value and unrealized P&L (amount and %)
- [x] `GET /api/portfolio/allocation`: stocks largest first, then cash, each with value and percent
- [x] `PortfolioService`: cash and holdings read together in one read-only transaction; prices applied afterwards from the quote cache, so no upstream call happens inside it
- [x] A quote that cannot be fetched leaves that holding unpriced and valued at cost, instead of failing the request
- [x] Fully sold positions are hidden from the list, but their realized P&L is still in the total

**Frontend**

- [x] `PortfolioOverview`: summary, holdings table and allocation bars, refreshed after every order
- [x] Result buttons are disabled while a request is pending, so a click cannot leave a quote that does not match the results shown

**Tests:** 44 in total (6 new): valuation and unrealized P&L, a loss and a closed position, allocation with two stocks and cash, the unpriced fallback, and per-user scoping. `StubFinnhub` gained per-symbol responses.

**Verified** in headless Chromium against the running backend and real Finnhub: buying AAPL through the UI produced total value `$100,000.00` = cash `$98,308.00` + invested `$1,692.00` (5 × `$338.40`), with matching allocation of `1.69%` / `98.31%`.

**Decisions made building it**

- Null fields are omitted from the JSON, so the client types mark `price` and `unrealizedPnl` optional rather than nullable.
- Allocation uses the same valuation as the portfolio (unpriced holdings at cost), so the two endpoints always agree.
- A large portfolio costs one cached quote per symbol; there is no per-user throttle (see the existing gap on the 60/min limit).

## Slice 5 — Charts ✅

**Goal:** a user viewing a stock sees its price history as candlesticks over a chosen range.

**The provider changed.** Finnhub's `/stock/candle` returns `403 You don't have access to
this resource` on the free tier (checked against the real key), so candles come from
**Twelve Data** (free plan: about 8 calls/minute, 800/day). Finnhub still serves quotes and search.

**Backend**

- [x] `GET /api/stocks/{symbol}/candles?range=1D|1W|1M|6M|1Y|5Y` (default `1M`), oldest first, prices as strings, time as UTC epoch seconds
- [x] `TwelveDataClient` behind a 10-minute Caffeine cache; failures are not cached
- [x] A closed set of ranges (`CandleRange`), so a caller cannot spend the small quota on arbitrary provider requests
- [x] Provider errors mapped: unknown symbol `404`, rate limit / refused / unreadable / no key `503`; malformed bars dropped
- [x] `TWELVEDATA_API_KEY` is optional: blank, the app runs and only the candles endpoint answers `503`

**Frontend**

- [x] `PriceChart` (Lightweight Charts candlesticks, range buttons, loading and error states) inside the trade panel

**Tests:** 53 in total (9 new, in `CandleApiTest`), against a stub of the provider. Includes the request the provider receives, that repeat calls are cached, that a failure is not, and that ordering is fixed up (removing the sort makes the daily test fail).

**Verified** against the real Twelve Data API with a real key: `1M` returned 22 daily bars,
`1D` 80 five-minute bars, `5Y` 260 weekly bars, all `200`, and the last daily close (`338.40`)
matches Finnhub's live quote. The chart renders real AAPL history in headless Chromium, and a
rate-limit error shows its message. The response shape in `TwelveDataClient` matches what the
live API returns.

**Still to do**

- [ ] Volume bars under the candles
- [ ] `1D` returns 80 five-minute bars, which spans a little over one session (Friday's tail plus Monday); trim to the latest session if it matters

**Decisions made building it**

- Candles are cached, not stored: the `price_candles` table is not created. Nothing needs history older than the cache yet; slice 8's indicators can read the same cached series. Revisit if the daily quota bites.
- `5Y` uses weekly bars and `1D` uses 5-minute bars, to keep every range to a few hundred points.
- Times are requested in UTC so intraday bars need no exchange-timezone conversion. Daily and weekly bars are placed at midnight UTC of their date.

## Slice 6 — Pending orders ✅

**Goal:** a user can place a limit or stop-loss order that waits, fills by itself when the
price is reached, and can be cancelled while it waits.

**Backend**

- [x] `V4__pending_orders.sql`: `limit_price`, `trigger_price`, `attach_stop_loss` on `orders`
- [x] `POST /api/orders` takes `type` `MARKET | LIMIT | STOP_LOSS`, with `limitPrice` / `triggerPrice` as strings. A limit or stop-loss is risk-checked, then saved `PENDING`; mismatched fields are `400`
- [x] `DELETE /api/orders/{id}` cancels a pending order: `404` for one that is not the caller's, `422` for one already settled
- [x] `PendingOrderScheduler` (every 10s, `app.orders.poll-interval-ms`): reads the quote cache, and for each triggered order calls `TradingService.fillPending`
- [x] A buy limit fills at or below its limit, a sell limit at or above it, a stop-loss once the price falls to its trigger. **Fills happen at the price then available**, not at the limit or trigger, so a stop-loss that gaps fills lower
- [x] Risk is **re-run at fill time** against the user's cash and shares now; an order that no longer passes becomes `REJECTED` instead of filling
- [x] Shares promised to a pending sell cannot be promised to another sell, limit, stop-loss or market
- [x] `attachStopLoss` on a buy creates a `STOP_LOSS` sell for the same shares at the fill price less the user's `default_stop_loss_pct`

**Frontend**

- [x] Order type (Market / Limit / Stop-loss), price field, and the attach-stop-loss option in the trade panel; a stop-loss forces Sell
- [x] Order history shows the type, the limit or stop price while pending, and a Cancel button; the page re-reads every 15s while any order is pending

**Tests:** 66 in total (13 new, in `PendingOrderApiTest`). The scheduler is driven by calling `runOnce()` directly, not by waiting on a timer, and the timer is switched off in tests so it cannot race them. Checked to fail when broken:
removing the "still pending" guard fails the deterministic test and the race test; removing the row lock fails the fill-versus-cancel race test 3 runs out of 3; and counting an order against its own shares fails 4 tests.

**Verified** in a browser against real MySQL and real Finnhub, with the real timer: a limit at `$100` stayed pending and cancelled cleanly; a limit at `$1,000` filled by itself within the poll interval at `$338.40`, with no page reload; a market buy with the option ticked created a pending stop-loss at `$321.48` (5% below `$338.40`).

**Bugs the tests found while building it**

- A new order is saved `PENDING` before its risk check, so its own quantity was counted as "already promised" and a sell of every share held was refused. It is now excluded from its own check (found by two existing tests).
- A limit price came back as `95.00` from the request but `95.0000` when read back from MySQL. Prices are now held at the column's scale when the order is created.

**Decisions made building it**

- **Every write locks the user row first**: placing, filling and cancelling. That is what makes a fill and a cancel safe against each other. Nothing may read before the lock, because MySQL fixes a transaction's snapshot at its first plain `SELECT` and would show a stale status.
- **No cash reservation for pending buys**, per the plan: cash is re-checked at fill time. So a user can place more pending buys than they can afford, and the ones that cannot be paid for are rejected when they trigger.
- A marketable limit (already at or past its price) is not filled at placement; it waits for the next scheduler pass, up to about 10s.
- Cancelling and filling are both refused politely when they lose a race: a cancel that loses gets `422`, and a fill that loses does nothing.

## Decisions made

| Date | Decision | Note |
|---|---|---|
| 2026-09-28 | TypeScript on the frontend | Money crosses the wire as strings; types prevent silent numeric coercion |
| 2026-09-28 | Finnhub as the market data provider | Free tier covers quotes and search. **Candles turned out to be paid-only** (see slice 5), so the original reason was only partly true |
| 2026-09-28 | Currency is USD, starting balance `$100,000` | Follows from Finnhub's free tier being US equities |
| 2026-09-28 | Build feature by feature, each slice verified end-to-end | Avoids a large untested backend with no UI behind it |
| 2026-09-29 | **Flyway migrations**, not `schema.sql` | Each slice adds a `V<n>__*.sql`; `ddl-auto=validate` makes Hibernate check the entities still match |
| 2026-09-29 | MySQL 8.4 LTS via brew, not Docker | `mysql@8` is not a formula; 8.4 is the current MySQL 8 LTS |
| 2026-09-29 | Duplicate email returns `422`, not `409` | Keeps to the status codes ARCHITECTURE §10 already lists — a well-formed request failing a business rule |
| 2026-09-29 | Money formatted to a string in the DTO, not by a Jackson setting | `UserResponse.from` calls `toPlainString()`, so rule 1 is visible at the boundary rather than depending on serializer config |
| 2026-09-29 | `.env` read through `spring.config.import`, no extra dependency | The same keys work as real environment variables in a deployed environment |
| 2026-09-29 | The cache sits on `FinnhubClient`, not `MarketService` | `@Cacheable` does not cache thrown exceptions, so caching above the 404 check would let an unknown symbol hit Finnhub on every request |
| 2026-09-29 | Upstream failure returns `503`, a code ARCHITECTURE §10 does not list | Nothing is wrong with this service and retrying is reasonable, which is neither a `500` nor a `422` |
| 2026-09-29 | Search results are written to `stocks`; the quote path is not | Search is the only call that returns a company name, and by the time a user can order a symbol they have searched it |
| 2026-09-29 | Finnhub JSON parsed with `USE_BIG_DECIMAL_FOR_FLOATS` | Rule 1 starts at the process boundary. **The reason first written here, and in the slice 2 commit message, was wrong:** the default does *not* turn `197.33` into `197.32999999999998` — Jackson's `DoubleNode.decimalValue()` goes through `Double.toString`, which round-trips ~15 significant digits exactly. The setting only bites past that (`9007199254740993.005` → `…994.00` without it, `…993.01` with it). Kept as defence in depth, not as the thing that saves ordinary prices |
| 2026-09-29 | Testcontainers MySQL 8.4 for tests, not H2 | The migrations are MySQL-specific and slice 3 needs real row locking; the `portfoliopro` user also cannot create a second schema locally |
| 2026-09-29 | `api.version=1.44` pinned for Surefire | Testcontainers' bundled docker-java negotiates API 1.32, which Docker Engine 29+ refuses outright; it is a floor, so any newer daemon still works |
| 2026-09-29 | No `@Validated` on `MarketController` | Spring 6.1+ validates constrained controller parameters itself; the annotation replaces that with an AOP proxy whose raw `ConstraintViolationException` surfaced as a `500` |

## Open questions

- **Indian equities?** The original design used `₹1,00,000`. Finnhub's free tier does
  not cover NSE/BSE, so the app is currently USD. Revisit only if Indian symbols are
  a hard requirement — it would mean changing the data provider.
- **Candle storage:** decided for now to cache, not store (see slice 5). Revisit if slice 8's
  indicators or the daily quota need more history than the cache holds.

## Known gaps / deliberate deferrals

- **Only the buy race and the fill/cancel race are tested.** Concurrent sells of the same shares are covered by the same lock but have no test of their own.
- **The scheduler assumes one running instance.** Two would both try to fill an order; the lock and the pending check keep that safe (the loser does nothing), but they would double the Finnhub calls.
- **Every symbol with a pending order costs a Finnhub call each time its 15s quote expires**, so many distinct pending symbols can approach the 60/min limit.
- **Pending buys do not reserve cash** (see slice 6).
- **No frontend tests at all.** `tsc` and ESLint are the only checks on the client.
- **`stocks.exchange` and `stocks.sector` are always null.** Finnhub's search payload
  carries neither; filling them needs a `/stock/profile2` call per symbol. Left for
  whenever a screen actually shows them — slice 8 needs profile data anyway.
- **Search filters hard** to US common stocks with no dot in the ticker, so `microsoft`
  returns exactly `MSFT`. Precise, but a broader query may return less than a user
  expects; worth revisiting once the search box exists.
- **The quote cache is keyed by symbol alone**, which is intended — ten users watching
  `AAPL` cost one upstream call — but it means no per-user throttling exists. A single
  user cycling through many symbols can still exhaust the 60/min free tier.
- No deployment setup. Local development only for now.
- Nothing is committed yet — git is initialised and the tree is staged, but there is no
  first commit.
