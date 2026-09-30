import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'

import { errorMessage } from '../api/client'
import { fetchQuote, placeOrder, searchStocks } from '../api/trading'
import { estimateValue, formatUsd, pnlColor } from '../lib/money'
import { FundamentalsCard } from './FundamentalsCard'
import { PriceChart } from './PriceChart'
import type { OrderSide, OrderType, PlaceOrderRequest, Quote, StockSearchResult } from '../types/trading'

const ORDER_TYPES: { value: OrderType; label: string }[] = [
  { value: 'MARKET', label: 'Market' },
  { value: 'LIMIT', label: 'Limit' },
  { value: 'STOP_LOSS', label: 'Stop-loss' },
]

interface TradePanelProps {
  /** Called after an order reaches the server, whatever its outcome, so the page can refresh. */
  onOrderPlaced: () => void
  /** Symbols already on the watchlist, so the button can say Watch or Unwatch. */
  watchedSymbols: string[]
  onToggleWatch: (symbol: string, watched: boolean) => void
  /**
   * Asks the panel to load a symbol, as when one is clicked on the watchlist. An object
   * rather than a string so choosing the same symbol twice is a new request.
   */
  requested: { symbol: string } | null
  className?: string
}

export function TradePanel({
  onOrderPlaced,
  watchedSymbols,
  onToggleWatch,
  requested,
  className = '',
}: TradePanelProps) {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<StockSearchResult[]>([])
  const [quote, setQuote] = useState<Quote | null>(null)
  const [side, setSide] = useState<OrderSide>('BUY')
  const [orderType, setOrderType] = useState<OrderType>('MARKET')
  const [price, setPrice] = useState('')
  const [attachStopLoss, setAttachStopLoss] = useState(false)
  const [quantity, setQuantity] = useState('1')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  useEffect(() => {
    if (requested === null) {
      return
    }
    let active = true
    fetchQuote(requested.symbol)
      .then((loaded) => {
        if (active) {
          setQuote(loaded)
          setResults([])
          setError(null)
          setNotice(null)
        }
      })
      .catch((failure: unknown) => {
        if (active) {
          setError(errorMessage(failure))
        }
      })
    return () => {
      active = false
    }
  }, [requested])

  async function handleSearch(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    setNotice(null)
    setQuote(null)
    try {
      const found = await searchStocks(query.trim())
      setResults(found)
      if (found.length === 0) {
        setError('No matching stocks')
      }
    } catch (failure) {
      setError(errorMessage(failure))
    } finally {
      setBusy(false)
    }
  }

  async function choose(symbol: string) {
    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      setQuote(await fetchQuote(symbol))
    } catch (failure) {
      setError(errorMessage(failure))
    } finally {
      setBusy(false)
    }
  }

  async function handleOrder(event: FormEvent) {
    event.preventDefault()
    if (quote === null) {
      return
    }
    const shares = Number(quantity)
    if (!Number.isInteger(shares) || shares < 1) {
      setError('Quantity must be a whole number of shares')
      return
    }
    const request: PlaceOrderRequest = { symbol: quote.symbol, side, type: orderType, quantity: shares }
    if (orderType !== 'MARKET') {
      // Kept as the text the user typed: money is never parsed into a number (rule 1).
      if (!/^\d+(\.\d{1,4})?$/.test(price) || Number(price) <= 0) {
        setError('Enter a price greater than zero, with at most 4 decimal places')
        return
      }
      if (orderType === 'LIMIT') {
        request.limitPrice = price
      } else {
        request.triggerPrice = price
      }
    }
    if (side === 'BUY' && orderType !== 'STOP_LOSS' && attachStopLoss) {
      request.attachStopLoss = true
    }
    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      const order = await placeOrder(request)
      setNotice(`${order.side} ${order.quantity} ${order.symbol}: ${order.status.toLowerCase()}`)
    } catch (failure) {
      // A rejected order is a 422 with the reason in the message, and is on the order list too.
      setError(errorMessage(failure))
    } finally {
      setBusy(false)
      onOrderPlaced()
    }
  }

  const watched = quote !== null && watchedSymbols.includes(quote.symbol)
  // What the order would be worth: the last price for a market order, the typed price otherwise.
  const referencePrice = orderType === 'MARKET' ? quote?.price : price
  const estimate = referencePrice === undefined ? null : estimateValue(referencePrice, Number(quantity))

  return (
    <section aria-labelledby="trade-heading" className={`section ${className}`}>
      <h2 id="trade-heading" className="section-title">
        Trade
      </h2>

      <form onSubmit={handleSearch} role="search" className="mt-4 flex gap-2">
        <input
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="A company or a ticker, e.g. Apple or AAPL"
          aria-label="Search stocks"
          maxLength={50}
          required
          className="input"
        />
        <button type="submit" disabled={busy} className="btn shrink-0">
          Search
        </button>
      </form>

      {results.length > 0 && (
        <ul className="mt-3 max-h-72 overflow-y-auto border-y border-rule">
          {results.map((result) => (
            <li key={result.symbol} className="border-t border-rule first:border-t-0">
              <button
                type="button"
                onClick={() => void choose(result.symbol)}
                // A click during a pending request would land after it and leave a quote
                // that does not belong to the results being shown.
                disabled={busy}
                className="flex min-h-11 w-full items-center justify-between gap-4 px-2 py-2 text-left transition-colors duration-150 hover:bg-accent-wash disabled:opacity-50"
              >
                <span className="font-medium">{result.symbol}</span>
                <span className="truncate text-sm text-ink-2">{result.name}</span>
              </button>
            </li>
          ))}
        </ul>
      )}

      {quote === null && results.length === 0 && error === null && (
        <p className="mt-4 max-w-md text-sm text-ink-2">
          Search for a company or a ticker to see its price, chart and fundamentals, then place an order.
        </p>
      )}

      {quote !== null && (
        <div className="mt-8">
          <div className="flex items-start justify-between gap-4">
            <div>
              <p className="eyebrow">{quote.symbol} last price</p>
              <p data-testid="quote-price" className="mt-1 text-2xl font-medium">
                {formatUsd(quote.price)}
              </p>
              {quote.change !== null && quote.percentChange !== null && (
                <p className={`mt-1 text-sm tabular-nums ${pnlColor(quote.change)}`}>
                  {Number(quote.change) > 0 ? '+' : ''}
                  {formatUsd(quote.change)} ({Number(quote.change) > 0 ? '+' : ''}
                  {quote.percentChange}%) today
                </p>
              )}
            </div>
            <button type="button" onClick={() => onToggleWatch(quote.symbol, watched)} className="btn btn-sm shrink-0">
              {watched ? 'Unwatch' : 'Watch'}
            </button>
          </div>

          <FundamentalsCard symbol={quote.symbol} />
          <PriceChart symbol={quote.symbol} />

          <form onSubmit={handleOrder} aria-labelledby="ticket-heading" className="mt-10 rounded-control border border-edge p-4 sm:p-6">
            <h3 id="ticket-heading" className="text-lg">
              Order ticket
            </h3>

            <div className="mt-4 flex flex-wrap gap-x-8 gap-y-4">
              <div>
                <p className="eyebrow mb-1">Order type</p>
                <div className="seg" role="group" aria-label="Order type">
                  {ORDER_TYPES.map((option) => (
                    <button
                      key={option.value}
                      type="button"
                      aria-pressed={orderType === option.value}
                      onClick={() => {
                        setOrderType(option.value)
                        // A stop-loss only ever sells.
                        if (option.value === 'STOP_LOSS') {
                          setSide('SELL')
                        }
                      }}
                    >
                      {option.label}
                    </button>
                  ))}
                </div>
              </div>
              <div>
                <p className="eyebrow mb-1">Side</p>
                <div className="seg" role="group" aria-label="Side">
                  {(['BUY', 'SELL'] as const).map((option) => (
                    <button
                      key={option}
                      type="button"
                      aria-pressed={side === option}
                      disabled={orderType === 'STOP_LOSS' && option === 'BUY'}
                      onClick={() => setSide(option)}
                    >
                      {option === 'BUY' ? 'Buy' : 'Sell'}
                    </button>
                  ))}
                </div>
              </div>
            </div>

            <div className="mt-5 grid grid-cols-2 gap-4 sm:max-w-md">
              <div>
                <label htmlFor="quantity" className="eyebrow block">
                  Shares
                </label>
                <input
                  id="quantity"
                  type="number"
                  min={1}
                  max={1000000}
                  step={1}
                  value={quantity}
                  onChange={(event) => setQuantity(event.target.value)}
                  className="input mt-1 tabular-nums"
                />
              </div>
              {orderType !== 'MARKET' && (
                <div>
                  <label htmlFor="price" className="eyebrow block">
                    {orderType === 'LIMIT' ? 'Limit price' : 'Trigger price'}
                  </label>
                  <input
                    id="price"
                    inputMode="decimal"
                    placeholder="0.00"
                    value={price}
                    onChange={(event) => setPrice(event.target.value)}
                    className="input mt-1 tabular-nums"
                  />
                </div>
              )}
            </div>

            {orderType === 'LIMIT' && (
              <p className="mt-3 max-w-md text-sm text-ink-2">
                A {side === 'BUY' ? 'buy fills at or below' : 'sell fills at or above'} this price, at the price
                then available. It waits as pending until then.
              </p>
            )}
            {orderType === 'STOP_LOSS' && (
              <p className="mt-3 max-w-md text-sm text-ink-2">
                Sells once the price falls to this trigger, at the price then available, which can be lower.
              </p>
            )}
            {side === 'BUY' && orderType !== 'STOP_LOSS' && (
              <label className="mt-4 flex min-h-11 items-center gap-3 text-sm text-ink-2">
                <input
                  type="checkbox"
                  checked={attachStopLoss}
                  onChange={(event) => setAttachStopLoss(event.target.checked)}
                  className="size-5 accent-accent"
                />
                Also place a stop-loss below the fill price (your default percentage)
              </label>
            )}

            <div className="mt-5 flex flex-wrap items-center justify-between gap-4 border-t border-rule pt-5">
              <p className="text-sm text-ink-2">
                {orderType === 'MARKET' ? 'Estimated value at the last price' : 'Estimated value at your price'}
                <span className="ml-3 text-lg font-medium tabular-nums text-ink">{estimate ?? '—'}</span>
              </p>
              <button type="submit" disabled={busy} className="btn btn-primary w-full sm:w-auto">
                {orderType === 'MARKET' ? 'Place market order' : `Place ${orderType === 'LIMIT' ? 'limit' : 'stop-loss'} order`}
              </button>
            </div>
          </form>
        </div>
      )}

      {error !== null && (
        <p role="alert" className="notice-error mt-4">
          {error}
        </p>
      )}
      {notice !== null && (
        <p role="status" className="mt-4 border-l-2 border-gain py-1 pl-3 text-sm text-gain">
          {notice}
        </p>
      )}
    </section>
  )
}
