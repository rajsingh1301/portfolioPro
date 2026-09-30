import { useEffect, useState } from 'react'

import { errorMessage } from '../api/client'
import { fetchFundamentals } from '../api/trading'
import { formatCompactUsd, formatUsd } from '../lib/money'
import type { Fundamentals } from '../types/trading'

type Loaded = { symbol: string; data: Fundamentals } | { symbol: string; error: string }

function Stat({ label, value }: { label: string; value: string | undefined }) {
  return (
    <div>
      <dt className="text-xs text-slate-500">{label}</dt>
      <dd className="mt-0.5 text-sm font-medium text-slate-900">{value ?? '—'}</dd>
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
    return <p className="mt-5 text-sm text-slate-500">Loading fundamentals…</p>
  }
  if ('error' in current) {
    return <p className="mt-5 text-sm text-slate-500">{current.error}</p>
  }

  const f = current.data
  const range =
    f.week52Low !== undefined && f.week52High !== undefined
      ? `${formatUsd(f.week52Low)} – ${formatUsd(f.week52High)}`
      : undefined
  return (
    <div className="mt-5">
      <h3 className="text-sm font-medium text-slate-500">
        Fundamentals
        {(f.industry !== undefined || f.exchange !== undefined) && (
          <span className="ml-2 font-normal">{[f.industry, f.exchange].filter(Boolean).join(' · ')}</span>
        )}
      </h3>
      <dl className="mt-2 grid grid-cols-2 gap-4 sm:grid-cols-4">
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
