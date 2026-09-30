import { AreaSeries, LineStyle, createChart } from 'lightweight-charts'
import type { IChartApi, ISeriesApi, Time } from 'lightweight-charts'
import { useEffect, useMemo, useRef, useState } from 'react'

import { errorMessage } from '../api/client'
import { fetchPerformance } from '../api/trading'
import { baseChartOptions, chartColors, withAlpha } from '../lib/chartTheme'
import { formatSignedUsd, formatUsd, pnlColor } from '../lib/money'
import { PERFORMANCE_RANGES } from '../types/trading'
import type { DayMove, Performance, PerformanceRange, PositionResult } from '../types/trading'

type Loaded = { key: string; data: Performance } | { key: string; error: string }

/** A UTC calendar day, e.g. "2026-09-29", as text. It is a date, not a moment, so no time zone is applied. */
function formatDay(day: string): string {
  return new Date(`${day}T00:00:00Z`).toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    timeZone: 'UTC',
  })
}

const cents = (amount: string) => Math.round(Number(amount) * 100)

/** Signed change and percent between two money strings, in whole cents so no float error creeps in. */
function changeBetween(from: string, to: string): { amount: string; percent: string } {
  const diff = cents(to) - cents(from)
  const base = cents(from)
  const percent = base === 0 ? 0 : (diff / base) * 100
  return { amount: (diff / 100).toFixed(2), percent: percent.toFixed(2) }
}

function signedPercent(percent: string): string {
  return Number(percent) > 0 ? `+${percent}%` : `${percent}%`
}

function Tile({ label, value, sub, tone }: { label: string; value: string; sub?: string; tone?: string }) {
  return (
    <div>
      <dt className="eyebrow">{label}</dt>
      <dd className={`mt-1 text-lg font-medium ${tone ?? 'text-ink'}`}>{value}</dd>
      {sub !== undefined && <dd className="text-xs text-ink-3">{sub}</dd>}
    </div>
  )
}

function DayTile({ label, move }: { label: string; move: DayMove | undefined }) {
  return move === undefined ? (
    <Tile label={label} value="None" sub="nothing moved that way" tone="text-ink-3" />
  ) : (
    <Tile
      label={label}
      value={formatSignedUsd(move.change)}
      sub={`${signedPercent(move.changePercent)} · ${formatDay(move.date)}`}
      tone={pnlColor(move.change)}
    />
  )
}

/**
 * How the portfolio has done, beside what each position contributed. Both come from one
 * request, so the chart and the bars always describe the same moment.
 *
 * @param version bumped by the page after an order, so the numbers follow the trade
 */
export function PerformanceSection({ version }: { version: number }) {
  const [range, setRange] = useState<PerformanceRange>('3M')
  const [loaded, setLoaded] = useState<Loaded | null>(null)
  const [hoverDay, setHoverDay] = useState<string | null>(null)
  const containerRef = useRef<HTMLDivElement>(null)
  const chartRef = useRef<IChartApi | null>(null)
  const seriesRef = useRef<ISeriesApi<'Area'> | null>(null)
  const startLineRef = useRef<ReturnType<ISeriesApi<'Area'>['createPriceLine']> | null>(null)

  const key = `${range}:${version}`

  useEffect(() => {
    let active = true
    fetchPerformance(range)
      .then((data) => {
        if (active) {
          setLoaded({ key, data })
        }
      })
      .catch((failure: unknown) => {
        if (active) {
          setLoaded({ key, error: errorMessage(failure, 'Could not load your performance') })
        }
      })
    return () => {
      active = false
    }
  }, [range, version, key])

  // While a new range or a fresh trade reloads, the previous figures stay, dimmed: no blank frame.
  const shown = loaded !== null && 'data' in loaded ? loaded.data : null
  const failed = loaded !== null && 'error' in loaded && loaded.key === key ? loaded.error : null
  const refreshing = loaded !== null && loaded.key !== key
  const points = useMemo(() => shown?.points ?? [], [shown])
  const hasCurve = points.length >= 2

  // One chart for the life of the component, created only once there is a curve to draw.
  useEffect(() => {
    const container = containerRef.current
    if (container === null || !hasCurve) {
      return
    }
    const c = chartColors()
    const chart = createChart(container, baseChartOptions(c))
    chartRef.current = chart
    // One series, so no legend: the section's title says what it is. The area is a wash, the line is 2px.
    seriesRef.current = chart.addSeries(AreaSeries, {
      lineColor: c.series[0],
      topColor: withAlpha(c.series[0], 0.12),
      bottomColor: withAlpha(c.series[0], 0),
      lineWidth: 2,
      priceLineVisible: false,
      // Dollars with separators on the axis, like everywhere else on the page.
      priceFormat: {
        type: 'custom',
        minMove: 0.01,
        formatter: (price: number) =>
          price.toLocaleString('en-US', { style: 'currency', currency: 'USD', minimumFractionDigits: 0, maximumFractionDigits: 2 }),
      },
      crosshairMarkerRadius: 4,
      crosshairMarkerBorderColor: c.paper,
      crosshairMarkerBorderWidth: 2,
    })
    const onMove = (param: { time?: unknown }) => setHoverDay(typeof param.time === 'string' ? param.time : null)
    chart.subscribeCrosshairMove(onMove)
    return () => {
      chart.unsubscribeCrosshairMove(onMove)
      chart.remove()
      chartRef.current = null
      seriesRef.current = null
      startLineRef.current = null
    }
  }, [hasCurve])

  useEffect(() => {
    const series = seriesRef.current
    if (series === null || shown === null || !hasCurve) {
      return
    }
    series.setData(shown.points.map((point) => ({ time: point.date as Time, value: Number(point.value) })))
    if (startLineRef.current !== null) {
      series.removePriceLine(startLineRef.current)
    }
    // Where the range began, so "up" and "down" have a line to be measured against.
    startLineRef.current = series.createPriceLine({
      price: Number(shown.summary.startValue),
      color: chartColors().ink3,
      lineWidth: 1,
      lineStyle: LineStyle.Dotted,
      axisLabelVisible: false,
      title: 'Start',
    })
    chartRef.current?.timeScale().fitContent()
  }, [shown, hasCurve])

  const byDay = useMemo(() => new Map(points.map((point) => [point.date, point])), [points])
  const readout = (hoverDay !== null ? byDay.get(hoverDay) : undefined) ?? points[points.length - 1]
  const sinceStart = readout !== undefined && shown !== null ? changeBetween(shown.summary.startValue, readout.value) : null

  return (
    <div className="mt-12 grid gap-12 lg:grid-cols-[minmax(0,1fr)_22rem] lg:items-start lg:gap-x-14">
      <section aria-labelledby="performance-heading" className="section">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 id="performance-heading" className="section-title">
            Performance
          </h2>
          <div className="seg seg-sm" role="group" aria-label="Performance range">
            {PERFORMANCE_RANGES.map((option) => (
              <button key={option} type="button" aria-pressed={range === option} onClick={() => setRange(option)}>
                {option}
              </button>
            ))}
          </div>
        </div>

        {shown === null ? (
          <p role={failed === null ? 'status' : 'alert'} className={failed === null ? 'mt-4 text-sm text-ink-3' : 'notice-error mt-4'}>
            {failed ?? 'Loading your performance…'}
          </p>
        ) : (
          <div className={`transition-opacity duration-200 ${refreshing ? 'opacity-50' : ''}`}>
            {hasCurve && (
              <dl className="mt-4 grid grid-cols-2 gap-x-8 gap-y-5 sm:grid-cols-3">
                <Tile
                  label="Change"
                  value={formatSignedUsd(shown.summary.change)}
                  sub={`${signedPercent(shown.summary.changePercent)} since ${formatDay(shown.from)}`}
                  tone={pnlColor(shown.summary.change)}
                />
                <DayTile label="Best day" move={shown.summary.bestDay} />
                <DayTile label="Worst day" move={shown.summary.worstDay} />
              </dl>
            )}

            {hasCurve ? (
              <>
                {readout !== undefined && sinceStart !== null && (
                  <dl
                    className="mt-6 flex flex-wrap items-baseline gap-x-5 gap-y-1 text-sm tabular-nums"
                    aria-label="Value under the pointer, or the latest"
                  >
                    <div className="text-ink-3">
                      <dt className="sr-only">Day</dt>
                      <dd>{formatDay(readout.date)}</dd>
                    </div>
                    <div className="flex gap-1.5">
                      <dt className="text-ink-3">Value</dt>
                      <dd className="font-medium">{formatUsd(readout.value)}</dd>
                    </div>
                    <div className="flex gap-1.5">
                      <dt className="text-ink-3">Since the start</dt>
                      <dd className={`font-medium ${pnlColor(sinceStart.amount)}`}>
                        {formatSignedUsd(sinceStart.amount)} ({signedPercent(sinceStart.percent)})
                      </dd>
                    </div>
                  </dl>
                )}
                <div ref={containerRef} className="mt-2 h-64 w-full" role="img" aria-label={`Portfolio value from ${formatDay(shown.from)} to ${formatDay(shown.to)}`} />
              </>
            ) : (
              <p className="mt-4 max-w-md text-sm text-ink-2">
                Your value will start moving once you hold something. Until then it is your cash: {formatUsd(shown.summary.endValue)}.
              </p>
            )}

            {shown.estimatedSymbols.length > 0 && (
              <p className="mt-3 max-w-xl text-xs text-ink-3">
                {shown.estimatedSymbols.join(', ')} {shown.estimatedSymbols.length === 1 ? 'is' : 'are'} valued at trade
                prices on the days no price history was available, so the curve is an estimate there.
              </p>
            )}

            {hasCurve && (
              <details className="mt-3 text-sm">
                <summary className="flex min-h-11 items-center text-ink-2 hover:text-ink">Show the data as a table</summary>
                <div className="max-h-72 overflow-auto" tabIndex={0} role="region" aria-label="Performance data table">
                  <table className="data-table">
                    <caption className="pb-2 text-left text-xs text-ink-3">
                      Latest {Math.min(points.length, 40)} of {points.length} days, newest first
                    </caption>
                    <thead>
                      <tr>
                        <th scope="col">Day</th>
                        <th scope="col" className="num">Value</th>
                        <th scope="col" className="num hidden sm:table-cell">Cash</th>
                        <th scope="col" className="num hidden sm:table-cell">In shares</th>
                      </tr>
                    </thead>
                    <tbody>
                      {points.slice(-40).reverse().map((point) => (
                        <tr key={point.date}>
                          <td className="whitespace-nowrap">{formatDay(point.date)}</td>
                          <td className="num">{formatUsd(point.value)}</td>
                          <td className="num hidden sm:table-cell">{formatUsd(point.cash)}</td>
                          <td className="num hidden sm:table-cell">{formatUsd(point.invested)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </details>
            )}
          </div>
        )}
      </section>

      <PositionResults positions={shown?.positions ?? null} dimmed={refreshing} />
    </div>
  )
}

/**
 * What each position has contributed since the account opened, as bars around zero: gains to the
 * right, losses to the left, every value written out. The bars are thin and only the outer end
 * is rounded, so they read as lengths from a common baseline.
 */
function PositionResults({ positions, dimmed }: { positions: PositionResult[] | null; dimmed: boolean }) {
  const total = positions?.reduce((sum, position) => sum + cents(position.totalPnl), 0) ?? 0
  const widest = Math.max(1, ...(positions ?? []).map((position) => Math.abs(cents(position.totalPnl))))
  return (
    <section aria-labelledby="results-heading" className="section">
      <h2 id="results-heading" className="section-title">
        Result by position
      </h2>
      <p className="mt-2 text-sm text-ink-2">Realized plus unrealized, since the account opened.</p>
      {positions === null ? (
        <p role="status" className="mt-4 text-sm text-ink-3">
          Loading…
        </p>
      ) : positions.length === 0 ? (
        <p className="mt-4 max-w-xs text-sm text-ink-2">No positions yet. Each one you open or close will be listed here with what it made or lost.</p>
      ) : (
        <div className={`transition-opacity duration-200 ${dimmed ? 'opacity-50' : ''}`}>
          <ul className="mt-3">
            {positions.map((position) => {
              const value = cents(position.totalPnl)
              const share = (Math.abs(value) / widest) * 50 // half the track is one side of zero
              return (
                <li
                  key={position.symbol}
                  className="grid grid-cols-[4.5rem_minmax(0,1fr)_auto] items-center gap-3 border-t border-rule py-3 first:border-t-0"
                >
                  <span className="text-sm">
                    <span className="block font-medium">{position.symbol}</span>
                    <span className="block text-xs text-ink-3">{position.open ? `${position.quantity} held` : 'closed'}</span>
                  </span>
                  <span aria-hidden className="relative h-3">
                    <span className="absolute inset-y-0 left-1/2 w-px bg-edge" />
                    <span
                      className={`absolute inset-y-0 ${value >= 0 ? 'left-1/2 rounded-r-control bg-gain' : 'right-1/2 rounded-l-control bg-loss'}`}
                      style={{ width: `${share}%` }}
                    />
                  </span>
                  <span className={`w-24 text-right text-sm font-medium tabular-nums ${pnlColor(position.totalPnl)}`}>
                    {formatSignedUsd(position.totalPnl)}
                  </span>
                </li>
              )
            })}
          </ul>
          <p className="flex items-baseline justify-between border-t border-ink pt-3 text-sm">
            <span className="text-ink-2">All positions</span>
            <span className={`font-medium tabular-nums ${pnlColor(String(total))}`}>{formatSignedUsd((total / 100).toFixed(2))}</span>
          </p>
        </div>
      )}
    </section>
  )
}
