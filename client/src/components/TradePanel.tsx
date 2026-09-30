import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'

import { errorMessage } from '../api/client'
import { fetchQuote, placeOrder, searchStocks } from '../api/trading'
import { formatUsd } from '../lib/money'
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
}

export function TradePanel({ onOrderPlaced, watchedSymbols, onToggleWatch, requested }: TradePanelProps) {
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

  return (
    <section className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
      <h2 className="text-lg font-semibold text-slate-900">Trade</h2>

      <form onSubmit={handleSearch} className="mt-4 flex gap-2">
        <input
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Search a company or ticker"
          aria-label="Search stocks"
          maxLength={50}
          required
          className="w-full rounded-md border border-slate-300 px-3 py-2 text-slate-900 outline-none focus:border-slate-900 focus:ring-1 focus:ring-slate-900"
        />
        <button
          type="submit"
          disabled={busy}
          className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white transition hover:bg-slate-700 disabled:opacity-50"
        >
          Search
        </button>
      </form>

      {results.length > 0 && (
        <ul className="mt-3 divide-y divide-slate-100 rounded-md border border-slate-200">
          {results.map((result) => (
            <li key={result.symbol}>
              <button
                type="button"
                onClick={() => void choose(result.symbol)}
                // A click during a pending request would land after it and leave a quote
                // that does not belong to the results being shown.
                disabled={busy}
                className="flex w-full items-center justify-between px-3 py-2 text-left text-sm hover:bg-slate-50 disabled:opacity-50"
              >
                <span className="font-medium text-slate-900">{result.symbol}</span>
                <span className="text-slate-500">{result.name}</span>
              </button>
            </li>
          ))}
        </ul>
      )}

      {quote !== null && (
        <form onSubmit={handleOrder} className="mt-5 border-t border-slate-100 pt-5">
          <div className="flex items-center justify-between">
            <p className="text-sm text-slate-500">{quote.symbol} last price</p>
            <button
              type="button"
              onClick={() => onToggleWatch(quote.symbol, watchedSymbols.includes(quote.symbol))}
              className="rounded-md border border-slate-300 px-2.5 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50"
            >
              {watchedSymbols.includes(quote.symbol) ? 'Unwatch' : 'Watch'}
            </button>
          </div>
          <p className="text-2xl font-semibold text-slate-900">{formatUsd(quote.price)}</p>

          <FundamentalsCard symbol={quote.symbol} />
          <PriceChart symbol={quote.symbol} />

          <div className="mt-4 flex flex-wrap items-end gap-3">
            <div className="inline-flex overflow-hidden rounded-md border border-slate-300" role="group" aria-label="Order type">
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
                  className={`px-3 py-2 text-sm font-medium ${
                    orderType === option.value ? 'bg-slate-900 text-white' : 'bg-white text-slate-700 hover:bg-slate-50'
                  }`}
                >
                  {option.label}
                </button>
              ))}
            </div>
            <div className="inline-flex overflow-hidden rounded-md border border-slate-300" role="group" aria-label="Side">
              {(['BUY', 'SELL'] as const).map((option) => (
                <button
                  key={option}
                  type="button"
                  aria-pressed={side === option}
                  disabled={orderType === 'STOP_LOSS' && option === 'BUY'}
                  onClick={() => setSide(option)}
                  className={`px-4 py-2 text-sm font-medium ${
                    side === option ? 'bg-slate-900 text-white' : 'bg-white text-slate-700 hover:bg-slate-50'
                  } disabled:opacity-40`}
                >
                  {option === 'BUY' ? 'Buy' : 'Sell'}
                </button>
              ))}
            </div>
            <div>
              <label htmlFor="quantity" className="block text-xs font-medium text-slate-500">
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
                className="mt-1 w-28 rounded-md border border-slate-300 px-3 py-2 text-slate-900 outline-none focus:border-slate-900 focus:ring-1 focus:ring-slate-900"
              />
            </div>
            {orderType !== 'MARKET' && (
              <div>
                <label htmlFor="price" className="block text-xs font-medium text-slate-500">
                  {orderType === 'LIMIT' ? 'Limit price' : 'Trigger price'}
                </label>
                <input
                  id="price"
                  inputMode="decimal"
                  placeholder="0.00"
                  value={price}
                  onChange={(event) => setPrice(event.target.value)}
                  className="mt-1 w-32 rounded-md border border-slate-300 px-3 py-2 text-slate-900 outline-none focus:border-slate-900 focus:ring-1 focus:ring-slate-900"
                />
              </div>
            )}
            <button
              type="submit"
              disabled={busy}
              className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white transition hover:bg-slate-700 disabled:opacity-50"
            >
              {orderType === 'MARKET' ? 'Place market order' : `Place ${orderType === 'LIMIT' ? 'limit' : 'stop-loss'} order`}
            </button>
          </div>
          {orderType === 'LIMIT' && (
            <p className="mt-2 text-xs text-slate-500">
              A {side === 'BUY' ? 'buy fills at or below' : 'sell fills at or above'} this price, at the price
              then available. It waits as pending until then.
            </p>
          )}
          {orderType === 'STOP_LOSS' && (
            <p className="mt-2 text-xs text-slate-500">
              Sells once the price falls to this trigger, at the price then available, which can be lower.
            </p>
          )}
          {side === 'BUY' && orderType !== 'STOP_LOSS' && (
            <label className="mt-3 flex items-center gap-2 text-sm text-slate-700">
              <input
                type="checkbox"
                checked={attachStopLoss}
                onChange={(event) => setAttachStopLoss(event.target.checked)}
              />
              Also place a stop-loss below the fill price (your default percentage)
            </label>
          )}
        </form>
      )}

      {error !== null && (
        <p role="alert" className="mt-4 text-sm text-red-600">
          {error}
        </p>
      )}
      {notice !== null && <p className="mt-4 text-sm text-green-700">{notice}</p>}
    </section>
  )
}
