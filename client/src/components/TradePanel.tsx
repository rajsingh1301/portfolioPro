import { useState } from 'react'
import type { FormEvent } from 'react'

import { errorMessage } from '../api/client'
import { fetchQuote, placeOrder, searchStocks } from '../api/trading'
import { formatUsd } from '../lib/money'
import type { OrderSide, Quote, StockSearchResult } from '../types/trading'

interface TradePanelProps {
  /** Called after an order reaches the server, whatever its outcome, so the page can refresh. */
  onOrderPlaced: () => void
}

export function TradePanel({ onOrderPlaced }: TradePanelProps) {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<StockSearchResult[]>([])
  const [quote, setQuote] = useState<Quote | null>(null)
  const [side, setSide] = useState<OrderSide>('BUY')
  const [quantity, setQuantity] = useState('1')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

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
    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      const order = await placeOrder({ symbol: quote.symbol, side, type: 'MARKET', quantity: shares })
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
          <p className="text-sm text-slate-500">{quote.symbol} last price</p>
          <p className="text-2xl font-semibold text-slate-900">{formatUsd(quote.price)}</p>

          <div className="mt-4 flex flex-wrap items-end gap-3">
            <div className="inline-flex overflow-hidden rounded-md border border-slate-300" role="group" aria-label="Side">
              {(['BUY', 'SELL'] as const).map((option) => (
                <button
                  key={option}
                  type="button"
                  aria-pressed={side === option}
                  onClick={() => setSide(option)}
                  className={`px-4 py-2 text-sm font-medium ${
                    side === option ? 'bg-slate-900 text-white' : 'bg-white text-slate-700 hover:bg-slate-50'
                  }`}
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
            <button
              type="submit"
              disabled={busy}
              className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white transition hover:bg-slate-700 disabled:opacity-50"
            >
              Place market order
            </button>
          </div>
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
