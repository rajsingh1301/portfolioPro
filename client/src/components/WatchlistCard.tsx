import { formatUsd, pnlColor } from '../lib/money'
import type { WatchlistItem } from '../types/trading'

interface WatchlistCardProps {
  items: WatchlistItem[]
  error: string | null
  onSelect: (symbol: string) => void
  onRemove: (symbol: string) => void
  className?: string
}

function change(item: WatchlistItem): string {
  if (item.change === undefined || item.percentChange === undefined) {
    return '—'
  }
  const sign = Number(item.change) > 0 ? '+' : ''
  return `${sign}${formatUsd(item.change)} (${sign}${item.percentChange}%)`
}

export function WatchlistCard({ items, error, onSelect, onRemove, className = '' }: WatchlistCardProps) {
  return (
    <section aria-labelledby="watchlist-heading" className={`section ${className}`}>
      <h2 id="watchlist-heading" className="section-title">
        Watchlist
      </h2>
      {error !== null && (
        <p role="alert" className="notice-error mt-3">
          {error}
        </p>
      )}
      {items.length === 0 ? (
        <p className="mt-4 text-sm text-ink-2">
          Nothing followed yet. Find a stock in the trade panel and choose Watch.
        </p>
      ) : (
        <ul className="mt-2">
          {items.map((item) => (
            <li key={item.symbol} className="flex items-center gap-2 border-t border-rule first:border-t-0">
              <button
                type="button"
                onClick={() => onSelect(item.symbol)}
                aria-label={`Trade ${item.symbol}`}
                className="-mx-2 flex min-h-11 min-w-0 flex-1 items-center justify-between gap-3 rounded-control px-2 py-2 text-left transition-colors duration-150 hover:bg-accent-wash"
              >
                <span className="min-w-0">
                  <span className="block font-medium">{item.symbol}</span>
                  {item.name !== undefined && <span className="block truncate text-xs text-ink-3">{item.name}</span>}
                </span>
                <span className="shrink-0 text-right tabular-nums">
                  <span className="block text-sm">{item.price === undefined ? '—' : formatUsd(item.price)}</span>
                  <span className={`block text-xs ${pnlColor(item.change)}`}>{change(item)}</span>
                </span>
              </button>
              <button
                type="button"
                onClick={() => onRemove(item.symbol)}
                aria-label={`Stop watching ${item.symbol}`}
                className="btn btn-sm shrink-0"
              >
                Remove
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
