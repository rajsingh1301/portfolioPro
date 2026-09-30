# PortfolioPro

A paper-trading app: search US stocks, watch live prices and charts, and trade with virtual
money. Everything runs against real market data, so the numbers are real and the money is not.

**What it does**

- A landing page at `/` for visitors who are not signed in (the dashboard, for those who are).
- Sign up and get a virtual `$100,000`.
- A dense, dark trading workspace (with a light theme): a candlestick chart with volume, the
  watchlist and an order ticket beside it, and holdings, open orders and history below. The panels
  resize, collapse and remember their sizes, and become tabs on a phone.
- Search any US stock with **Ctrl+K** (or **/**). **B** and **S** open the order ticket to buy or
  sell the symbol on screen.
- Live quotes (refreshed every 15 seconds), technical indicators (SMA, EMA, RSI, MACD, Bollinger
  Bands) with plain-words readings, and company fundamentals.
- Trade at market, or place limit and stop-loss orders that a scheduler fills when the price
  is reached. Optionally attach a stop-loss to a buy.
- A portfolio page: total value, day P&L and overall P&L, sortable holdings, allocation, how the
  value has moved over a week to all time, and what each position has made or lost.
- Set your own risk limits (max position size, max order value, default stop-loss).

Indicator readings describe and never advise, and indicators are frequently wrong.

## Stack

Spring Boot 4 / Java 21 and MySQL 8.4 on the backend, React 19 / TypeScript / Tailwind on the
client. Quotes and fundamentals come from [Finnhub](https://finnhub.io), chart history from
[Twelve Data](https://twelvedata.com), both on their free plans.

[ARCHITECTURE.md](ARCHITECTURE.md) is the design and the rules that must always hold (money is
`BigDecimal`, an order is one transaction, the user row is locked before cash is touched, and
so on). [PROGRESS.md](PROGRESS.md) is what was built, slice by slice, the decisions behind it,
and what is still open.

## Running it

You need **Java 21+**, **Node 22+**, **MySQL 8.4**, and **Docker** (only for the tests).

```bash
brew services start mysql@8.4
```

Create the database and user once (any MySQL password works; put the same one in `.env`):

```sql
CREATE DATABASE portfoliopro;
CREATE USER 'portfoliopro'@'localhost' IDENTIFIED BY '<password>';
GRANT ALL ON portfoliopro.* TO 'portfoliopro'@'localhost';
```

Then the two env files, both git-ignored. Copy the example beside each and fill it in:

```bash
cp server/.env.example server/.env   # DB password, JWT secret, FINNHUB_API_KEY, TWELVEDATA_API_KEY
cp client/.env.example client/.env   # VITE_API_BASE_URL, already correct for local use
```

- `FINNHUB_API_KEY`: free at https://finnhub.io/dashboard. Required.
- `TWELVEDATA_API_KEY`: free at https://twelvedata.com. Optional; without it everything works
  except the chart, which says "Chart data is not configured".
- `JWT_SECRET`: at least 32 bytes, e.g. `openssl rand -base64 48`.

```bash
cd server && ./mvnw spring-boot:run     # API on :8080; Flyway creates and migrates the schema
cd client && npm install && npm run dev # UI on :5173
```

## Tests

```bash
cd server && ./mvnw test                # 159 tests, ~1 min, needs Docker running
cd client && npx tsc -b && npx eslint . # type-check and lint (there are no client unit tests yet)
```

The server tests run against a real MySQL 8.4 in Testcontainers (not H2: the migrations are
MySQL-specific and the order path depends on real row locking). Finnhub and Twelve Data are
never called from them: local stubs stand in, and count the requests they receive.

### End to end

With the backend and client running and real keys in `server/.env`:

```bash
cd e2e && npm install && npm run install-browser
npm run journey     # the whole app, step by step
npm run a11y        # axe-core (WCAG 2.2 AA) on every page, both themes, desktop and phone
```

The journey signs up a fresh account and walks the whole app in a headless browser (the command
palette, watching, indicators, keyboard trading, market/limit/stop-loss orders, the scheduler
filling one, a risk limit rejecting an order, every page, the theme, resizing panels, a failing
endpoint and its Retry, log out and back in, and the phone layout), stopping at the first thing
that is wrong. The audit also checks the keyboard focus ring and, on a phone, every tap target.
To audit an account that already has history, set `A11Y_EMAIL` and `A11Y_PASSWORD`.

## Design

A dense trading terminal: near-black panels, 1px hairlines, a 3px radius, no shadows or gradients, a
13px body with tabular figures, green and red only for up and down (always with an arrow and a sign),
one blue for primary actions. Dark by default, light behind a toggle. Every colour, size and radius is a
token in [`client/src/index.css`](client/src/index.css); the charts read the same variables. Contrast
was measured on every surface text can sit on, which is why a few colours differ from TradingView's
own: see [PROGRESS.md](PROGRESS.md#trading-terminal-ui).

## Not done

Deployment (it runs locally only) and client unit tests. See "Known gaps" in
[PROGRESS.md](PROGRESS.md).
