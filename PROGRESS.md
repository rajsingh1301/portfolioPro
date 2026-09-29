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
| 2 | Market data + cache | 🟡 Backend done, frontend pending |
| 3 | Trading (market orders) | ⬜ Not started |
| 4 | Portfolio | ⬜ Not started |
| 5 | Charts | ⬜ Not started |
| 6 | Pending orders (limit, stop-loss) | ⬜ Not started |
| 7 | Watchlist | ⬜ Not started |
| 8 | Analysis (indicators, fundamentals) | ⬜ Not started |
| 9 | Risk settings UI | ⬜ Not started |

Legend: ✅ done · 🟡 partial · ⬜ not started

---

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

**Not verified:** the rendered UI in a real browser — no browser was driven. `tsc` and
ESLint are clean and the production build succeeds, but the pages have not been
clicked through.

---

## Slice 2 — Market data + cache 🟡

**Goal:** an authenticated user can search symbols and see a live quote, with Finnhub
called at most once per symbol per TTL. — backend met; the UI is not built yet.

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

- [ ] `api/market.ts` typed client
- [ ] Symbol search box
- [ ] Quote panel

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

**Not verified:** no UI exists for this slice yet, and there are still no automated
tests. Behaviour when Finnhub is actually down or rate-limiting was not exercised — the
`503` path is reasoned, not observed.

## Decisions made

| Date | Decision | Note |
|---|---|---|
| 2026-09-28 | TypeScript on the frontend | Money crosses the wire as strings; types prevent silent numeric coercion |
| 2026-09-28 | Finnhub as the market data provider | Free tier covers quotes, candles and fundamentals |
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
| 2026-09-29 | Finnhub JSON parsed with `USE_BIG_DECIMAL_FOR_FLOATS` | Rule 1 starts at the process boundary — the default `double` would turn `197.33` into `197.32999999999998` |
| 2026-09-29 | No `@Validated` on `MarketController` | Spring 6.1+ validates constrained controller parameters itself; the annotation replaces that with an AOP proxy whose raw `ConstraintViolationException` surfaced as a `500` |

## Open questions

- **Indian equities?** The original design used `₹1,00,000`. Finnhub's free tier does
  not cover NSE/BSE, so the app is currently USD. Revisit only if Indian symbols are
  a hard requirement — it would mean changing the data provider.
- **Candle storage:** cache Finnhub candles in `price_candles`, or proxy them live
  every time? Matters from slice 5, and the indicators in slice 8 need stored candles.

## Known gaps / deliberate deferrals

- **No tests yet**, and slice 1 shipped without any. Slice 3 (the order transaction) is
  where they become genuinely necessary — concurrency and rollback cannot be verified
  by clicking. Worth adding a `@SpringBootTest` slice for auth at the same time.
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
