import { assignSlots } from '../lib/slots'
import { formatSignedUsd, formatUsd, pnlColor } from '../lib/money'
import type { AllocationSlice, Portfolio } from '../types/trading'

function Stat({ label, value, tone }: { label: string; value: string; tone?: string }) {
  return (
    <div>
      <dt className="eyebrow">{label}</dt>
      <dd className={`mt-1 text-lg font-medium ${tone ?? 'text-ink'}`}>{value}</dd>
    </div>
  )
}

/**
 * What the page leads with: one number, large, then the figures that explain it. No box
 * around it; the rule above the smaller figures is what groups them.
 */
export function PortfolioMasthead({ portfolio, cash }: { portfolio: Portfolio | null; cash: string }) {
  const unpriced = portfolio?.holdings.some((holding) => holding.price === undefined) ?? false
  return (
    <section aria-label="Portfolio summary" aria-busy={portfolio === null}>
      <div className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_minmax(0,26rem)] lg:items-end lg:gap-16">
        <dl>
          <dt className="eyebrow">Total value</dt>
          <dd className="mt-2 text-hero font-medium tracking-tight">
            {portfolio === null ? <span className="text-ink-3">…</span> : formatUsd(portfolio.totalValue)}
          </dd>
        </dl>
        <dl className="grid grid-cols-2 gap-x-8 gap-y-5 border-t border-ink pt-4">
          <Stat label="Cash" value={formatUsd(portfolio?.cash ?? cash)} />
          <Stat label="Invested" value={portfolio === null ? '…' : formatUsd(portfolio.holdingsValue)} />
          <Stat
            label="Unrealized P&L"
            value={portfolio === null ? '…' : formatSignedUsd(portfolio.unrealizedPnl)}
            tone={pnlColor(portfolio?.unrealizedPnl)}
          />
          <Stat
            label="Realized P&L"
            value={portfolio === null ? '…' : formatSignedUsd(portfolio.realizedPnl)}
            tone={pnlColor(portfolio?.realizedPnl)}
          />
        </dl>
      </div>
      {unpriced && (
        <p className="mt-4 text-sm text-ink-2">
          Some prices are unavailable right now; those holdings are shown at cost.
        </p>
      )}
    </section>
  )
}

function signedPercent(percent: string): string {
  return Number(percent) > 0 ? `+${percent}%` : `${percent}%`
}

export function HoldingsSection({ portfolio, className = '' }: { portfolio: Portfolio | null; className?: string }) {
  return (
    <section aria-labelledby="holdings-heading" className={`section ${className}`}>
      <h2 id="holdings-heading" className="section-title">
        Holdings
      </h2>
      {portfolio === null ? (
        <p role="status" className="mt-4 text-sm text-ink-3">
          Loading holdings…
        </p>
      ) : portfolio.holdings.length === 0 ? (
        <p className="mt-4 max-w-md text-sm text-ink-2">
          No positions yet. Search a stock in the trade panel and place an order to open one.
        </p>
      ) : (
        <div className="mt-3 overflow-x-auto">
          <table className="data-table">
            <thead>
              <tr>
                <th scope="col">Symbol</th>
                <th scope="col" className="num">Shares</th>
                <th scope="col" className="num hidden sm:table-cell">Avg cost</th>
                <th scope="col" className="num hidden sm:table-cell">Price</th>
                <th scope="col" className="num">Value</th>
                <th scope="col" className="num">Unrealized P&amp;L</th>
              </tr>
            </thead>
            <tbody>
              {portfolio.holdings.map((holding) => (
                <tr key={holding.symbol}>
                  <th scope="row" className="border-t border-rule py-3 pr-4 text-left align-top font-medium">
                    {holding.symbol}
                  </th>
                  <td className="num">{holding.quantity}</td>
                  <td className="num hidden sm:table-cell">{formatUsd(holding.avgPrice)}</td>
                  <td className="num hidden sm:table-cell">
                    {holding.price === undefined ? '—' : formatUsd(holding.price)}
                  </td>
                  <td className="num">{formatUsd(holding.marketValue)}</td>
                  <td className={`num ${pnlColor(holding.unrealizedPnl)}`}>
                    {holding.unrealizedPnl === undefined ? (
                      '—'
                    ) : (
                      <>
                        {formatSignedUsd(holding.unrealizedPnl)}
                        <span className="block text-xs">{signedPercent(holding.unrealizedPnlPercent ?? '0')}</span>
                      </>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}

const SLOTS = 6
/** Colours are CSS variables so the chart follows the tokens; the fills are neutral for cash and the fold. */
const CASH_FILL = 'color-mix(in srgb, var(--color-ink-3) 45%, var(--color-paper))'
const OTHER_FILL = 'color-mix(in srgb, var(--color-ink-3) 70%, var(--color-paper))'

interface Segment {
  key: string
  label: string
  percent: number
  value: string
  fill: string
}

/** Cents, so summing a few money strings for the "Other" fold never goes through a float sum of dollars. */
function toCents(amount: string): number {
  return Math.round(Number(amount) * 100)
}

function buildSegments(allocation: AllocationSlice[]): Segment[] {
  const stocks = allocation.filter((slice) => slice.symbol !== undefined)
  const slots = assignSlots(stocks.map((slice) => slice.symbol as string), SLOTS)
  const segments: Segment[] = []
  let foldedCents = 0
  let foldedPercent = 0
  let folded = 0
  for (const slice of stocks) {
    const slot = slots.get(slice.symbol as string)
    if (slot === undefined) {
      foldedCents += toCents(slice.value)
      foldedPercent += Number(slice.percent)
      folded += 1
      continue
    }
    segments.push({
      key: slice.label,
      label: slice.label,
      percent: Number(slice.percent),
      value: slice.value,
      fill: `var(--color-series-${slot + 1})`,
    })
  }
  if (folded > 0) {
    segments.push({
      key: 'other',
      label: `Other (${folded})`,
      percent: foldedPercent,
      value: (foldedCents / 100).toFixed(2),
      fill: OTHER_FILL,
    })
  }
  const cash = allocation.find((slice) => slice.symbol === undefined)
  if (cash !== undefined) {
    segments.push({ key: 'cash', label: 'Cash', percent: Number(cash.percent), value: cash.value, fill: CASH_FILL })
  }
  return segments
}

/**
 * Part-to-whole as one stacked bar, with the legend below doubling as the data table:
 * every value is readable without hovering and without telling colours apart.
 */
export function AllocationSection({ allocation, className = '' }: { allocation: AllocationSlice[]; className?: string }) {
  const segments = buildSegments(allocation)
  const summary = segments.map((segment) => `${segment.label} ${segment.percent.toFixed(2)}%`).join(', ')
  return (
    <section aria-labelledby="allocation-heading" className={`section ${className}`}>
      <h2 id="allocation-heading" className="section-title">
        Allocation
      </h2>
      {segments.length === 0 ? (
        <p role="status" className="mt-4 text-sm text-ink-3">
          Loading allocation…
        </p>
      ) : (
        <>
          <div
            role="img"
            aria-label={`Allocation: ${summary}`}
            className="mt-4 flex h-4 gap-0.5 overflow-hidden rounded-control bg-paper"
          >
            {segments.map((segment) => (
              <div
                key={segment.key}
                title={`${segment.label}: ${formatUsd(segment.value)} (${segment.percent.toFixed(2)}%)`}
                className="min-w-0.5 transition-opacity duration-150 hover:opacity-75"
                style={{ flex: `${Math.max(segment.percent, 0.1)} 1 0`, backgroundColor: segment.fill }}
              />
            ))}
          </div>
          <ul className="mt-4 text-sm">
            {segments.map((segment) => (
              <li key={segment.key} className="flex items-center gap-3 border-t border-rule py-2 first:border-t-0">
                <span aria-hidden className="size-3 shrink-0 rounded-control" style={{ backgroundColor: segment.fill }} />
                <span className="min-w-0 flex-1 truncate font-medium">{segment.label}</span>
                <span className="tabular-nums text-ink-2">{formatUsd(segment.value)}</span>
                <span className="w-16 text-right tabular-nums">{segment.percent.toFixed(2)}%</span>
              </li>
            ))}
          </ul>
        </>
      )}
    </section>
  )
}
