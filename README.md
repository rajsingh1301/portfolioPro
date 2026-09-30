# PortfolioPro

A paper-trading app: search US stocks, watch live prices and charts, and trade with virtual
money. Everything runs against real market data, so the numbers are real and the money is not.

**What it does**

- Sign up and get a virtual `$100,000`.
- Search stocks; see live quotes, candlestick charts, technical indicators (SMA, EMA, RSI,
  MACD, Bollinger Bands) with plain-words readings, and company fundamentals.
- Trade at market, or place limit and stop-loss orders that a scheduler fills when the price
  is reached. Optionally attach a stop-loss to a buy.
- See holdings, unrealized and realized P&L, and allocation. Follow stocks on a watchlist.
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
cd server && ./mvnw test                # 120 tests, ~1 min, needs Docker running
cd client && npx tsc -b && npx eslint . # type-check and lint (there are no client unit tests yet)
```

The server tests run against a real MySQL 8.4 in Testcontainers (not H2: the migrations are
MySQL-specific and the order path depends on real row locking). Finnhub and Twelve Data are
never called from them: local stubs stand in, and count the requests they receive.

### End to end

With the backend and client running and real keys in `server/.env`:

```bash
cd e2e && npm install && npm run install-browser && npm run journey
```

It signs up a fresh account and walks the whole app in a headless browser (search, chart,
indicators, watchlist, market/limit/stop-loss orders, the scheduler filling one, a risk limit
rejecting an order, reload, log out and back in), stopping at the first thing that is wrong.

## Not done

Deployment (it runs locally only) and client unit tests. See "Known gaps" in
[PROGRESS.md](PROGRESS.md).
