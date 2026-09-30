import { useMemo } from 'react'
import type { ReactNode } from 'react'

import { useShell } from '../../context/shellContext'
import { useTradingData } from '../../data/useTradingData'
import { formatUsd } from '../../lib/money'
import { useSort } from '../../lib/useSort'
import type { Holding } from '../../types/trading'
import { Change } from '../ui/Change'
import { PriceCell } from '../ui/PriceCell'
import { EmptyState, ErrorState, SkeletonRows } from '../ui/states'

type Key = 'symbol' | 'quantity' | 'avgPrice' | 'price' | 'marketValue' | 'pnl' | 'pnlPercent' | 'dayPnl' | 'allocation'

interface Row {
  holding: Holding
  dayPnl?: string
  allocation: number
}

const num = (value: string | undefined) => (value === undefined ? null : Number(value))

const ACCESSORS: Record<Key, (row: Row) => number | string | null> = {
  symbol: (row) => row.holding.symbol,
  quantity: (row) => row.holding.quantity,
  avgPrice: (row) => num(row.holding.avgPrice),
  price: (row) => num(row.holding.price),
  marketValue: (row) => num(row.holding.marketValue),
  pnl: (row) => num(row.holding.unrealizedPnl),
  pnlPercent: (row) => num(row.holding.unrealizedPnlPercent),
  dayPnl: (row) => num(row.dayPnl),
  allocation: (row) => row.allocation,
}

/**
 * Open positions: what is held, what it cost, what it is worth, what it has made, and what it made
 * today. Every column sorts; the last price flashes when it changes. Rows open the symbol.
 */
export function HoldingsTable() {
  const { portfolio, today, retry } = useTradingData()
  const { pickSymbol } = useShell()

  const rows = useMemo<Row[]>(() => {
    const data = portfolio.data
    if (data === null) {
      return []
    }
    const total = Number(data.totalValue)
    const dayBySymbol = new Map((today.data?.positions ?? []).map((position) => [position.symbol, position.dayPnl]))
    return data.holdings.map((holding) => ({
      holding,
      dayPnl: dayBySymbol.get(holding.symbol),
      allocation: total > 0 ? (Number(holding.marketValue) / total) * 100 : 0,
    }))
  }, [portfolio.data, today.data])

  const { sorted, toggle, ariaSort } = useSort<Row, Key>(rows, ACCESSORS, { key: 'marketValue', direction: 'desc' })

  if (portfolio.loading) {
    return <SkeletonRows rows={4} columns={6} />
  }
  if (portfolio.error !== null && portfolio.data === null) {
    return <ErrorState message={portfolio.error} onRetry={() => retry('portfolio')} />
  }
  if (rows.length === 0) {
    return <EmptyState>No open positions. Pick a symbol and place an order to open one.</EmptyState>
  }

  const header = (key: Key, label: string, numeric = true, startsAscending = false): ReactNode => (
    <th scope="col" aria-sort={ariaSort(key)} className={numeric ? 'num' : undefined}>
      <button type="button" onClick={() => toggle(key, startsAscending)} className="inline-flex min-h-5 items-center gap-1 uppercase tracking-[0.05em] hover:text-ink pointer-coarse:min-h-11">
        {label}
        <span aria-hidden className="w-2 text-[0.8em]">{ariaSort(key) === 'ascending' ? '▲' : ariaSort(key) === 'descending' ? '▼' : ''}</span>
      </button>
    </th>
  )

  return (
    <table className="grid-table">
      <caption className="sr-only">Open positions</caption>
      <thead>
        <tr>
          {header('symbol', 'Symbol', false, true)}
          {header('quantity', 'Qty')}
          {header('avgPrice', 'Avg price')}
          {header('price', 'Last')}
          {header('marketValue', 'Value')}
          {header('pnl', 'P&L')}
          {header('pnlPercent', 'P&L %')}
          {header('dayPnl', 'Day P&L')}
          {header('allocation', 'Alloc')}
        </tr>
      </thead>
      <tbody>
        {sorted.map(({ holding, dayPnl, allocation }) => (
          <tr key={holding.symbol}>
            <td className="font-semibold">
              <button type="button" onClick={() => pickSymbol(holding.symbol)} aria-label={`Open ${holding.symbol}`} className="-mx-1 min-h-6 rounded-control px-1 hover:underline pointer-coarse:min-h-11">
                {holding.symbol}
              </button>
            </td>
            <td className="num">{holding.quantity}</td>
            <td className="num">{formatUsd(holding.avgPrice)}</td>
            <td className="num"><PriceCell value={holding.price} /></td>
            <td className="num">{formatUsd(holding.marketValue)}</td>
            <td className="num"><Change amount={holding.unrealizedPnl} /></td>
            <td className="num"><Change percent={holding.unrealizedPnlPercent} /></td>
            <td className="num"><Change amount={dayPnl} /></td>
            <td className="num text-ink-2">{allocation.toFixed(1)}%</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
