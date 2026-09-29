import { formatUsd, pnlColor } from '../lib/money'
import type { AllocationSlice, Portfolio } from '../types/trading'

interface PortfolioOverviewProps {
  portfolio: Portfolio | null
  allocation: AllocationSlice[]
}

function Stat({ label, value, tone }: { label: string; value: string; tone?: string }) {
  return (
    <div>
      <p className="text-sm font-medium text-slate-500">{label}</p>
      <p className={`mt-1 text-xl font-semibold tracking-tight ${tone ?? 'text-slate-900'}`}>{value}</p>
    </div>
  )
}

export function PortfolioOverview({ portfolio, allocation }: PortfolioOverviewProps) {
  if (portfolio === null) {
    return null
  }
  const unpriced = portfolio.holdings.some((holding) => holding.price === undefined)

  return (
    <>
      <section className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="grid grid-cols-2 gap-6 md:grid-cols-5">
          <Stat label="Total value" value={formatUsd(portfolio.totalValue)} />
          <Stat label="Cash" value={formatUsd(portfolio.cash)} />
          <Stat label="Invested" value={formatUsd(portfolio.holdingsValue)} />
          <Stat label="Unrealized P&L" value={formatUsd(portfolio.unrealizedPnl)} tone={pnlColor(portfolio.unrealizedPnl)} />
          <Stat label="Realized P&L" value={formatUsd(portfolio.realizedPnl)} tone={pnlColor(portfolio.realizedPnl)} />
        </div>
        {unpriced && (
          <p className="mt-4 text-xs text-slate-500">
            Some prices are unavailable right now; those holdings are shown at cost.
          </p>
        )}
      </section>

      <section className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
        <h2 className="text-lg font-semibold text-slate-900">Holdings</h2>
        {portfolio.holdings.length === 0 ? (
          <p className="mt-3 text-sm text-slate-500">No holdings yet. Place an order to open a position.</p>
        ) : (
          <div className="mt-3 overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="text-xs uppercase text-slate-500">
                <tr>
                  <th className="py-2 pr-4 font-medium">Symbol</th>
                  <th className="py-2 pr-4 text-right font-medium">Shares</th>
                  <th className="py-2 pr-4 text-right font-medium">Avg cost</th>
                  <th className="py-2 pr-4 text-right font-medium">Price</th>
                  <th className="py-2 pr-4 text-right font-medium">Value</th>
                  <th className="py-2 text-right font-medium">Unrealized P&amp;L</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {portfolio.holdings.map((holding) => (
                  <tr key={holding.symbol}>
                    <td className="py-2 pr-4 font-medium text-slate-900">{holding.symbol}</td>
                    <td className="py-2 pr-4 text-right">{holding.quantity}</td>
                    <td className="py-2 pr-4 text-right">{formatUsd(holding.avgPrice)}</td>
                    <td className="py-2 pr-4 text-right">
                      {holding.price === undefined ? '—' : formatUsd(holding.price)}
                    </td>
                    <td className="py-2 pr-4 text-right">{formatUsd(holding.marketValue)}</td>
                    <td className={`py-2 text-right ${pnlColor(holding.unrealizedPnl)}`}>
                      {holding.unrealizedPnl === undefined
                        ? '—'
                        : `${formatUsd(holding.unrealizedPnl)} (${holding.unrealizedPnlPercent}%)`}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
        <h2 className="text-lg font-semibold text-slate-900">Allocation</h2>
        <ul className="mt-3 space-y-3">
          {allocation.map((slice) => (
            <li key={slice.label}>
              <div className="flex justify-between text-sm">
                <span className="font-medium text-slate-900">{slice.label}</span>
                <span className="text-slate-500">
                  {formatUsd(slice.value)} · {slice.percent}%
                </span>
              </div>
              <div
                className="mt-1 h-2 overflow-hidden rounded-full bg-slate-100"
                role="img"
                aria-label={`${slice.label} ${slice.percent}% of portfolio`}
              >
                <div
                  className={`h-full rounded-full ${slice.symbol === undefined ? 'bg-slate-400' : 'bg-slate-900'}`}
                  style={{ width: `${Math.min(100, Number(slice.percent))}%` }}
                />
              </div>
            </li>
          ))}
        </ul>
      </section>
    </>
  )
}
