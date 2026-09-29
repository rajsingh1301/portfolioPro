import { formatUsd, pnlColor } from '../lib/money'
import type { WatchlistItem } from '../types/trading'

interface WatchlistCardProps {
  items: WatchlistItem[]
  error: string | null
  onSelect: (symbol: string) => void
  onRemove: (symbol: string) => void
}

function change(item: WatchlistItem): string {
  if (item.change === undefined || item.percentChange === undefined) {
    return '—'
  }
  const sign = Number(item.change) > 0 ? '+' : ''
  return `${sign}${formatUsd(item.change)} (${sign}${item.percentChange}%)`
}

export function WatchlistCard({ items, error, onSelect, onRemove }: WatchlistCardProps) {
  return (
    <section className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
      <h2 className="text-lg font-semibold text-slate-900">Watchlist</h2>
      {error !== null && (
        <p role="alert" className="mt-2 text-sm text-red-600">
          {error}
        </p>
      )}
      {items.length === 0 ? (
        <p className="mt-3 text-sm text-slate-500">
          Nothing followed yet. Search a stock in the trade panel and choose Watch.
        </p>
      ) : (
        <ul className="mt-3 divide-y divide-slate-100">
          {items.map((item) => (
            <li key={item.symbol} className="flex items-center gap-3 py-2 text-sm">
              <button
                type="button"
                onClick={() => onSelect(item.symbol)}
                aria-label={`Trade ${item.symbol}`}
                className="flex min-w-0 flex-1 items-center justify-between gap-3 rounded-md px-1 py-1 text-left hover:bg-slate-50"
              >
                <span className="min-w-0">
                  <span className="font-medium text-slate-900">{item.symbol}</span>
                  {item.name !== undefined && <span className="ml-2 truncate text-slate-500">{item.name}</span>}
                </span>
                <span className="flex shrink-0 items-baseline gap-3">
                  <span className="text-slate-900">{item.price === undefined ? '—' : formatUsd(item.price)}</span>
                  <span className={`w-36 text-right ${pnlColor(item.change)}`}>{change(item)}</span>
                </span>
              </button>
              <button
                type="button"
                onClick={() => onRemove(item.symbol)}
                aria-label={`Stop watching ${item.symbol}`}
                className="rounded-md border border-slate-300 px-2 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50"
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
