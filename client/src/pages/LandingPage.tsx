import { Link } from 'react-router-dom'

import { useTheme } from '../context/useTheme'

/** What the app really does, one row each. Nothing here is a claim the code does not back. */
const FEATURES: { name: string; text: string }[] = [
  {
    name: 'Orders',
    text: 'Market orders fill at the latest quote. Limit and stop-loss orders wait as pending and are checked every 10 seconds while the server runs. A pending order moves no cash until it fills, and you can cancel it.',
  },
  {
    name: 'Portfolio',
    text: 'Holdings with average cost, day P&L and overall P&L against what you deposited; a value curve over 1W to ALL; an allocation donut and the same numbers as a table.',
  },
  {
    name: 'Analysis',
    text: 'SMA, EMA, RSI and MACD on the chart, with a plain reading for each ("RSI 71.2, above 70"). The wording describes the numbers; it never says buy or sell. Fundamentals sit under the chart.',
  },
  {
    name: 'Risk limits',
    text: 'You set a maximum share of the portfolio in one stock, a maximum order value and a default stop-loss. An order that breaks a limit is refused before it touches your cash, and the message says which.',
  },
  {
    name: 'Keyboard',
    text: 'Ctrl+K or / searches any US stock. B and S open the ticket on that side. The layout is panels you can resize and collapse, and it remembers them.',
  },
]

const LIMITS = [
  'It is a simulator. No real money moves and nothing here is a brokerage.',
  'Prices come from Finnhub and Twelve Data free tiers, and refresh every 15 seconds rather than streaming. Candles are limited by a small daily quota.',
  'Fills are at the quoted price. There is no order book, no slippage and no partial fill, so real trading will differ.',
  'Indicators describe past prices. They are not a forecast and not advice.',
]

function Shot({ name, alt }: { name: string; alt: string }) {
  const { theme } = useTheme()
  return (
    <img
      src={`/landing/${name}-${theme}.jpg`}
      alt={alt}
      width={1440}
      height={900}
      loading="lazy"
      className="block h-auto w-full border border-rule"
    />
  )
}

export function LandingPage() {
  const { theme, toggle } = useTheme()

  return (
    <div className="min-h-dvh bg-canvas">
      <header className="border-b border-rule">
        <div className="mx-auto flex max-w-6xl items-center gap-2 px-4 py-3">
          <span className="mr-auto text-lg font-semibold tracking-tight">PortfolioPro</span>
          <button type="button" className="btn btn-quiet" onClick={toggle}>
            {theme === 'dark' ? 'Light theme' : 'Dark theme'}
          </button>
          <Link to="/login" className="btn btn-quiet">
            Log in
          </Link>
          <Link to="/signup" className="btn btn-primary">
            Create account
          </Link>
        </div>
      </header>

      <main>
        <section className="mx-auto max-w-6xl px-4 pt-10 pb-8 sm:pt-16">
          <h1 className="max-w-3xl text-2xl font-semibold tracking-tight sm:text-[2.25rem] sm:leading-tight">
            Practise trading US stocks with real prices and no real money.
          </h1>
          <p className="mt-4 max-w-2xl text-lg text-ink-2">
            Sign up, get $100,000 of virtual cash, and trade from a dense, keyboard-driven terminal. Prices are real;
            the money is not.
          </p>
          <div className="mt-6 flex flex-wrap gap-2">
            <Link to="/signup" className="btn btn-primary">
              Create an account
            </Link>
            <Link to="/login" className="btn">
              Log in
            </Link>
          </div>
          <div className="mt-10">
            <Shot
              name="dashboard"
              alt="The trading terminal: a price chart with two moving averages, a watchlist and an order ticket on the right, and open positions below."
            />
          </div>
        </section>

        <section aria-labelledby="what" className="border-t border-rule">
          <div className="mx-auto grid max-w-6xl gap-8 px-4 py-10 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.4fr)]">
            <div>
              <h2 id="what" className="text-xl font-semibold">
                What it does
              </h2>
              <dl className="mt-4 divide-y divide-rule border-y border-rule">
                {FEATURES.map((feature) => (
                  <div key={feature.name} className="py-3">
                    <dt className="text-base font-semibold">{feature.name}</dt>
                    <dd className="mt-1 text-sm text-ink-2">{feature.text}</dd>
                  </div>
                ))}
              </dl>
            </div>
            <div className="lg:pt-10">
              <Shot
                name="portfolio"
                alt="The portfolio page: a summary strip of value, cash and P&L, the value curve, an allocation donut and the holdings table."
              />
            </div>
          </div>
        </section>

        <section aria-labelledby="limits" className="border-t border-rule">
          <div className="mx-auto max-w-6xl px-4 py-10">
            <h2 id="limits" className="text-xl font-semibold">
              What it is not
            </h2>
            <ul className="mt-4 max-w-3xl list-disc space-y-2 pl-5 text-sm text-ink-2">
              {LIMITS.map((limit) => (
                <li key={limit}>{limit}</li>
              ))}
            </ul>
            <div className="mt-6 flex flex-wrap gap-2">
              <Link to="/signup" className="btn btn-primary">
                Create an account
              </Link>
              <Link to="/login" className="btn">
                Log in
              </Link>
            </div>
          </div>
        </section>
      </main>

      <footer className="border-t border-rule">
        <p className="mx-auto max-w-6xl px-4 py-4 text-sm text-ink-3">
          PortfolioPro is a learning project. Market data is provided by Finnhub and Twelve Data.
        </p>
      </footer>
    </div>
  )
}
