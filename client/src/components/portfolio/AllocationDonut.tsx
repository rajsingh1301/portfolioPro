import { useTradingData } from '../../data/useTradingData'
import { formatUsd } from '../../lib/money'
import { createSlotAssigner } from '../../lib/slots'
import type { AllocationSlice } from '../../types/trading'
import { ErrorState } from '../ui/states'

/** Four named holdings, then the rest folded into Other, then cash: six segments at most. */
const SLOTS = 4
const assign = createSlotAssigner(SLOTS)

const CASH_FILL = 'color-mix(in srgb, var(--color-edge) 70%, var(--color-panel))'
const OTHER_FILL = 'color-mix(in srgb, var(--color-ink-3) 60%, var(--color-panel))'

interface Segment {
  key: string
  label: string
  percent: number
  value: string
  fill: string
}

/** Cents, so summing a few money strings for the "Other" fold never goes through a float sum of dollars. */
const toCents = (amount: string) => Math.round(Number(amount) * 100)

function buildSegments(allocation: AllocationSlice[]): Segment[] {
  const stocks = allocation.filter((slice) => slice.symbol !== undefined)
  const slots = assign(stocks.map((slice) => slice.symbol as string))
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
    segments.push({ key: slice.label, label: slice.label, percent: Number(slice.percent), value: slice.value, fill: `var(--color-series-${slot + 1})` })
  }
  if (folded > 0) {
    segments.push({ key: 'other', label: `Other (${folded})`, percent: foldedPercent, value: (foldedCents / 100).toFixed(2), fill: OTHER_FILL })
  }
  const cash = allocation.find((slice) => slice.symbol === undefined)
  if (cash !== undefined) {
    segments.push({ key: 'cash', label: 'Cash', percent: Number(cash.percent), value: cash.value, fill: CASH_FILL })
  }
  return segments
}

const RADIUS = 15.9155 // a circle of circumference 100, so a percentage is a dash length
const GAP = 0.7

/**
 * Where the money sits, as a donut, with the legend beside it doing double duty as the data
 * table: every value and percentage is written out, so the ring never has to be read by colour.
 */
export function AllocationDonut() {
  const { allocation, retry } = useTradingData()

  if (allocation.loading) {
    return (
      <div role="status" aria-label="Loading allocation" className="flex items-center gap-4 p-3">
        <div className="skeleton size-28 rounded-full" />
        <div className="flex-1 space-y-2">
          <div className="skeleton h-3.5" />
          <div className="skeleton h-3.5" />
          <div className="skeleton h-3.5" />
        </div>
      </div>
    )
  }
  if (allocation.error !== null && allocation.data === null) {
    return <ErrorState message={allocation.error} onRetry={() => retry('allocation')} />
  }

  const segments = buildSegments(allocation.data ?? [])
  const summary = segments.map((segment) => `${segment.label} ${segment.percent.toFixed(2)}%`).join(', ')
  // Each segment starts where the ones before it end; 25 puts the first at twelve o'clock.
  const starts = segments.map((_, index) => 25 - segments.slice(0, index).reduce((sum, earlier) => sum + earlier.percent, 0))
  return (
    <div className="flex flex-wrap items-center gap-4 p-3">
      <svg viewBox="0 0 42 42" role="img" aria-label={`Allocation: ${summary}`} className="size-28 shrink-0 -rotate-0">
        <circle cx="21" cy="21" r={RADIUS} fill="none" stroke="var(--color-rule)" strokeWidth="4.5" />
        {segments.map((segment, index) => {
          const length = Math.max(segment.percent - GAP, 0.01)
          return (
            <circle
              key={segment.key}
              cx="21"
              cy="21"
              r={RADIUS}
              fill="none"
              stroke={segment.fill}
              strokeWidth="4.5"
              strokeDasharray={`${length} ${100 - length}`}
              strokeDashoffset={starts[index]}
            >
              <title>{`${segment.label}: ${formatUsd(segment.value)} (${segment.percent.toFixed(2)}%)`}</title>
            </circle>
          )
        })}
      </svg>
      <ul className="min-w-44 flex-1 text-sm">
        {segments.map((segment) => (
          <li key={segment.key} className="flex items-center gap-2 border-b border-rule py-1 last:border-b-0">
            <span aria-hidden className="size-2.5 shrink-0 rounded-control" style={{ backgroundColor: segment.fill }} />
            <span className="min-w-0 flex-1 truncate font-medium">{segment.label}</span>
            <span className="tabular-nums text-ink-2">{formatUsd(segment.value)}</span>
            <span className="w-14 text-right tabular-nums">{segment.percent.toFixed(2)}%</span>
          </li>
        ))}
      </ul>
    </div>
  )
}
