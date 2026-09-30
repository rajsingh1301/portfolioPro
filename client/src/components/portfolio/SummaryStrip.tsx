import type { ReactNode } from 'react'

import { useTradingData } from '../../data/useTradingData'
import { formatUsd } from '../../lib/money'
import { Change } from '../ui/Change'
import type { ResourceName } from '../../data/tradingDataContext'

function Cell({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="min-w-0 border-b border-r border-rule px-3 py-2">
      <dt className="label">{label}</dt>
      <dd className="mt-0.5 truncate">{children}</dd>
    </div>
  )
}

function Pending() {
  return <span className="skeleton inline-block h-5 w-24 align-middle" role="status" aria-label="Loading" />
}

/**
 * The five numbers to check first: what it is worth, what it made today, what it has made in all,
 * what is in shares, and what is left in cash. Each cell loads and fails on its own, so one slow
 * request never blanks the rest.
 */
export function SummaryStrip() {
  const { portfolio, today, retry } = useTradingData()
  const data = portfolio.data
  const day = today.data

  const failed = (name: ResourceName, message: string | null, has: boolean) =>
    message !== null && !has ? (
      <button type="button" className="btn btn-sm" onClick={() => retry(name)} title={message}>
        Retry
      </button>
    ) : null

  return (
    <section aria-label="Portfolio summary">
      <dl className="grid grid-cols-2 border-l border-t border-rule bg-panel md:grid-cols-5">
        <Cell label="Total value">
          {data !== null ? (
            <span className="text-2xl font-semibold">{formatUsd(data.totalValue)}</span>
          ) : (
            (failed('portfolio', portfolio.error, false) ?? <Pending />)
          )}
        </Cell>
        <Cell label="Day P&L">
          {day !== null ? (
            <span className="text-lg font-semibold">
              <Change amount={day.dayPnl} percent={day.dayPnlPercent} />
            </span>
          ) : (
            (failed('today', today.error, false) ?? <Pending />)
          )}
        </Cell>
        <Cell label="Overall P&L">
          {data !== null ? (
            <span className="text-lg font-semibold">
              <Change amount={data.overallPnl} percent={data.overallPnlPercent} />
            </span>
          ) : (
            (failed('portfolio', portfolio.error, false) ?? <Pending />)
          )}
        </Cell>
        <Cell label="Invested">
          {data !== null ? <span className="text-lg font-semibold">{formatUsd(data.holdingsValue)}</span> : <Pending />}
        </Cell>
        <Cell label="Cash">
          {data !== null ? <span className="text-lg font-semibold">{formatUsd(data.cash)}</span> : <Pending />}
        </Cell>
      </dl>
      {(portfolio.error !== null && data !== null) || (today.error !== null && day !== null) ? (
        <p role="status" className="mt-1 text-xs text-ink-3">
          Showing the last figures received; the latest refresh failed and will be tried again shortly.
        </p>
      ) : null}
    </section>
  )
}
