import { useEffect, useState } from 'react'

import { errorMessage } from '../api/client'
import { fetchFundamentals } from '../api/trading'
import { formatCompactUsd, formatUsd } from '../lib/money'
import type { Fundamentals } from '../types/trading'

type Loaded = { symbol: string; data: Fundamentals } | { symbol: string; error: string }

function Stat({ label, value }: { label: string; value: string | undefined }) {
  return (
    <div className="border-t border-rule py-3 pr-4 [&:nth-child(-n+2)]:border-t-0 sm:[&:nth-child(-n+4)]:border-t-0">
      <dt className="text-xs text-ink-3">{label}</dt>
      <dd className="mt-0.5 text-base font-medium tabular-nums">{value ?? '—'}</dd>
    </div>
  )
}

export function FundamentalsCard({ symbol }: { symbol: string }) {
  const [loaded, setLoaded] = useState<Loaded | null>(null)

  useEffect(() => {
    let active = true
    fetchFundamentals(symbol)
      .then((data) => {
        if (active) {
          setLoaded({ symbol, data })
        }
      })
      .catch((failure: unknown) => {
        if (active) {
          setLoaded({ symbol, error: errorMessage(failure, 'Could not load fundamentals') })
        }
      })
    return () => {
      active = false
    }
  }, [symbol])

  // A result for a previous symbol is treated as still loading, never shown under this one.
  const current = loaded !== null && loaded.symbol === symbol ? loaded : null
  if (current === null) {
    return (
      <p role="status" className="mt-6 text-sm text-ink-3">
        Loading fundamentals…
      </p>
    )
  }
  if ('error' in current) {
    return <p className="mt-6 text-sm text-ink-2">{current.error}</p>
  }

  const f = current.data
  const range =
    f.week52Low !== undefined && f.week52High !== undefined
      ? `${formatUsd(f.week52Low)} – ${formatUsd(f.week52High)}`
      : undefined
  const context = [f.name, f.industry, f.exchange].filter(Boolean).join(' · ')
  return (
    <div className="mt-6">
      <h3 className="flex flex-wrap items-baseline gap-x-3 text-lg">
        Fundamentals
        {context !== '' && <span className="font-sans text-xs font-normal text-ink-3">{context}</span>}
      </h3>
      <dl className="mt-2 grid grid-cols-2 border-t border-ink sm:grid-cols-4">
        <Stat label="Market cap" value={f.marketCap === undefined ? undefined : formatCompactUsd(f.marketCap)} />
        <Stat label="P/E (TTM)" value={f.peRatio} />
        <Stat label="EPS (TTM)" value={f.eps === undefined ? undefined : formatUsd(f.eps)} />
        <Stat label="Return on equity" value={f.roe === undefined ? undefined : `${f.roe}%`} />
        <Stat label="Dividend yield" value={f.dividendYield === undefined ? undefined : `${f.dividendYield}%`} />
        <Stat label="52-week range" value={range} />
        <Stat label="Beta" value={f.beta} />
      </dl>
    </div>
  )
}
