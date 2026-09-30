import { useEffect, useState } from 'react'

import { errorMessage } from '../../api/client'
import { fetchFundamentals } from '../../api/trading'
import { formatCompactUsd, formatUsd } from '../../lib/money'
import type { Fundamentals } from '../../types/trading'

type Loaded = { symbol: string; data: Fundamentals } | { symbol: string; error: string }

function Stat({ label, value }: { label: string; value: string | undefined }) {
  return (
    <div className="min-w-24">
      <dt className="text-xs text-ink-3">{label}</dt>
      <dd className="text-sm font-medium">{value ?? '—'}</dd>
    </div>
  )
}

/** One dense row of the company's headline ratios, above the chart on the full-page view. */
export function FundamentalsStrip({ symbol }: { symbol: string }) {
  const [loaded, setLoaded] = useState<Loaded | null>(null)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    let active = true
    fetchFundamentals(symbol)
      .then((data) => active && setLoaded({ symbol, data }))
      .catch((failure: unknown) => active && setLoaded({ symbol, error: errorMessage(failure, 'Could not load fundamentals') }))
    return () => {
      active = false
    }
  }, [symbol, attempt])

  // A result for a previous symbol is treated as still loading, never shown under this one.
  const current = loaded !== null && loaded.symbol === symbol ? loaded : null
  if (current === null) {
    return <div role="status" aria-label="Loading fundamentals" className="skeleton m-2.5 h-8" />
  }
  if ('error' in current) {
    return (
      <div role="alert" className="flex items-center gap-2 border-b border-rule px-2.5 py-1.5 text-sm">
        <span className="text-ink-2">{current.error}</span>
        <button type="button" className="btn btn-sm" onClick={() => setAttempt((n) => n + 1)}>
          Retry
        </button>
      </div>
    )
  }

  const f = current.data
  const range =
    f.week52Low !== undefined && f.week52High !== undefined ? `${formatUsd(f.week52Low)} – ${formatUsd(f.week52High)}` : undefined
  const context = [f.name, f.industry, f.exchange].filter(Boolean).join(' · ')
  return (
    <section aria-label="Fundamentals" className="border-b border-rule px-2.5 py-1.5">
      {context !== '' && <p className="mb-1 truncate text-xs text-ink-3">{context}</p>}
      <dl className="flex flex-wrap gap-x-6 gap-y-1">
        <Stat label="Market cap" value={f.marketCap === undefined ? undefined : formatCompactUsd(f.marketCap)} />
        <Stat label="P/E (TTM)" value={f.peRatio} />
        <Stat label="EPS (TTM)" value={f.eps === undefined ? undefined : formatUsd(f.eps)} />
        <Stat label="ROE" value={f.roe === undefined ? undefined : `${f.roe}%`} />
        <Stat label="Dividend yield" value={f.dividendYield === undefined ? undefined : `${f.dividendYield}%`} />
        <Stat label="52-week range" value={range} />
        <Stat label="Beta" value={f.beta} />
      </dl>
    </section>
  )
}
