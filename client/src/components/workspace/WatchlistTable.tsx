import { useState } from 'react'
import { Link } from 'react-router-dom'

import { useShell } from '../../context/shellContext'
import { useTradingData } from '../../data/useTradingData'
import { Change } from '../ui/Change'
import { PriceCell } from '../ui/PriceCell'
import { EmptyState, ErrorState, SkeletonRows } from '../ui/states'

interface WatchlistTableProps {
  /** The symbol on screen, highlighted in the list. */
  activeSymbol?: string
  /** `rail` is the compact list beside the chart; `page` adds the company name and a chart link. */
  variant?: 'rail' | 'page'
  /** Called when a row is chosen. Defaults to opening it on the trading screen. */
  onSelect?: (symbol: string) => void
}

export function WatchlistTable({ activeSymbol, variant = 'rail', onSelect }: WatchlistTableProps) {
  const { watchlist, toggleWatch, retry } = useTradingData()
  const { pickSymbol } = useShell()
  const [error, setError] = useState<string | null>(null)
  const select = onSelect ?? pickSymbol
  const items = watchlist.data ?? []
  const page = variant === 'page'

  async function remove(symbol: string) {
    setError(await toggleWatch(symbol, true))
  }

  return (
    <section aria-labelledby={`watchlist-heading-${variant}`} className="panel h-full">
      <div className="panel-header">
        <h2 id={`watchlist-heading-${variant}`}>Watchlist{items.length > 0 ? ` · ${items.length}` : ''}</h2>
        <span className="hidden text-ink-3 normal-case tracking-normal sm:inline">
          <kbd className="kbd">/</kbd> to add
        </span>
      </div>
      {error !== null && <p role="alert" className="notice-error m-2">{error}</p>}
      <div className="min-h-0 flex-1 overflow-auto">
        {watchlist.loading ? (
          <SkeletonRows rows={5} columns={4} />
        ) : watchlist.error !== null && watchlist.data === null ? (
          <ErrorState message={watchlist.error} onRetry={() => retry('watchlist')} />
        ) : items.length === 0 ? (
          <EmptyState>
            Nothing on your watchlist yet. Press <kbd className="kbd">/</kbd>, find a symbol and open it, then choose Watch.
          </EmptyState>
        ) : (
          <table className="grid-table">
            <caption className="sr-only">Watchlist: last price and change for each symbol</caption>
            <thead>
              <tr>
                <th scope="col">Symbol</th>
                {page && <th scope="col">Name</th>}
                <th scope="col" className="num">Last</th>
                <th scope="col" className="num">Chg</th>
                <th scope="col" className="num">Chg %</th>
                <th scope="col"><span className="sr-only">Actions</span></th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => {
                const active = item.symbol === activeSymbol
                return (
                  <tr key={item.symbol} aria-current={active ? 'true' : undefined} className={active ? 'bg-selected' : undefined}>
                    <td className="font-semibold">
                      <button
                        type="button"
                        onClick={() => select(item.symbol)}
                        aria-label={`Open ${item.symbol}`}
                        className="-mx-1 min-h-6 rounded-control px-1 text-left hover:underline pointer-coarse:min-h-11"
                      >
                        {item.symbol}
                      </button>
                    </td>
                    {page && <td className="max-w-56 truncate text-ink-2">{item.name ?? '—'}</td>}
                    <td className="num"><PriceCell value={item.price} /></td>
                    <td className="num"><Change amount={item.change} /></td>
                    <td className="num"><Change percent={item.percentChange} /></td>
                    <td className="text-right">
                      {page && (
                        <Link to={`/charts?symbol=${encodeURIComponent(item.symbol)}`} className="btn btn-sm mr-1">
                          Chart
                        </Link>
                      )}
                      <button
                        type="button"
                        onClick={() => void remove(item.symbol)}
                        aria-label={`Remove ${item.symbol} from the watchlist`}
                        className="btn btn-quiet btn-sm px-1.5"
                      >
                        <span aria-hidden>×</span>
                      </button>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        )}
      </div>
    </section>
  )
}
