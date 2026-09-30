import { AreaSeries, LineStyle, createChart } from 'lightweight-charts'
import type { IChartApi, ISeriesApi, Time } from 'lightweight-charts'
import { useEffect, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'

import { errorMessage } from '../../api/client'
import { fetchPerformance } from '../../api/trading'
import { useTheme } from '../../context/useTheme'
import { useTradingData } from '../../data/useTradingData'
import { baseChartOptions, chartColors, withAlpha } from '../../lib/chartTheme'
import { changeBetween, formatUsd } from '../../lib/money'
import type { DayMove, Performance, PerformanceRange, PositionResult } from '../../types/trading'
import { Change } from '../ui/Change'

/** The ranges the portfolio page offers. The API also has 3M and 6M. */
const RANGES: PerformanceRange[] = ['1W', '1M', '1Y', 'ALL']

type Loaded = { key: string; data: Performance } | { key: string; error: string }

/** A UTC calendar day, e.g. "2026-09-29", as text. It is a date, not a moment, so no time zone is applied. */
function formatDay(day: string): string {
  return new Date(`${day}T00:00:00Z`).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric', timeZone: 'UTC' })
}

function Tile({ label, children, sub }: { label: string; children: ReactNode; sub?: string }) {
  return (
    <div>
      <dt className="label">{label}</dt>
      <dd className="text-lg font-semibold">{children}</dd>
      {sub !== undefined && <dd className="text-xs text-ink-3">{sub}</dd>}
    </div>
  )
}

function DayTile({ label, move }: { label: string; move: DayMove | undefined }) {
  return move === undefined ? (
    <Tile label={label} sub="nothing moved that way">
      <span className="text-ink-3">None</span>
    </Tile>
  ) : (
    <Tile label={label} sub={`${formatDay(move.date)}`}>
      <Change amount={move.change} percent={move.changePercent} />
    </Tile>
  )
}

/**
 * The value over time, and beside it (as its own panel) what each position contributed. Both come
 * from one request, so they always describe the same moment. It is re-read after every trade.
 */
export function PerformancePanels() {
  const { theme } = useTheme()
  const { version } = useTradingData()
  const [range, setRange] = useState<PerformanceRange>('1M')
  const [loaded, setLoaded] = useState<Loaded | null>(null)
  const [attempt, setAttempt] = useState(0)
  const [hoverDay, setHoverDay] = useState<string | null>(null)
  const containerRef = useRef<HTMLDivElement>(null)
  const chartRef = useRef<IChartApi | null>(null)
  const seriesRef = useRef<ISeriesApi<'Area'> | null>(null)
  const startLineRef = useRef<ReturnType<ISeriesApi<'Area'>['createPriceLine']> | null>(null)

  const key = `${range}:${version}:${attempt}`

  useEffect(() => {
    let active = true
    fetchPerformance(range)
      .then((data) => active && setLoaded({ key, data }))
      .catch((failure: unknown) => active && setLoaded({ key, error: errorMessage(failure, 'Could not load your performance') }))
    return () => {
      active = false
    }
  }, [range, version, attempt, key])

  // While a new range or a fresh trade reloads, the previous figures stay, dimmed: no blank frame.
  const shown = loaded !== null && 'data' in loaded ? loaded.data : null
  const failed = loaded !== null && 'error' in loaded && loaded.key === key ? loaded.error : null
  const refreshing = loaded !== null && loaded.key !== key
  const points = useMemo(() => shown?.points ?? [], [shown])
  const hasCurve = points.length >= 2

  // One chart, created once there is a curve to draw.
  useEffect(() => {
    const container = containerRef.current
    if (container === null || !hasCurve) {
      return
    }
    const c = chartColors()
    const chart = createChart(container, baseChartOptions(c))
    chartRef.current = chart
    // One series, so no legend: the panel's title says what it is. A 2px line over a faint wash.
    seriesRef.current = chart.addSeries(AreaSeries, {
      lineColor: c.series[0],
      topColor: withAlpha(c.series[0], 0.16),
      bottomColor: withAlpha(c.series[0], 0),
      lineWidth: 2,
      priceLineVisible: false,
      priceFormat: {
        type: 'custom',
        minMove: 0.01,
        formatter: (price: number) => price.toLocaleString('en-US', { style: 'currency', currency: 'USD', minimumFractionDigits: 0, maximumFractionDigits: 2 }),
      },
      crosshairMarkerRadius: 4,
      crosshairMarkerBorderColor: c.panel,
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

  // A theme switch restyles the chart in place, from the CSS variables the switch has just set.
  useEffect(() => {
    const chart = chartRef.current
    const series = seriesRef.current
    if (chart === null || series === null) {
      return
    }
    const c = chartColors()
    chart.applyOptions(baseChartOptions(c))
    series.applyOptions({
      lineColor: c.series[0],
      topColor: withAlpha(c.series[0], 0.16),
      bottomColor: withAlpha(c.series[0], 0),
      crosshairMarkerBorderColor: c.panel,
    })
  }, [theme])

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
  }, [shown, hasCurve, theme])

  const byDay = useMemo(() => new Map(points.map((point) => [point.date, point])), [points])
  const readout = (hoverDay !== null ? byDay.get(hoverDay) : undefined) ?? points[points.length - 1]
  const sinceStart = readout !== undefined && shown !== null ? changeBetween(shown.summary.startValue, readout.value) : null

  return (
    <>
      <section aria-labelledby="performance-heading" className="panel">
        <div className="panel-header">
          <h2 id="performance-heading">Performance</h2>
          <div className="seg" role="group" aria-label="Performance range">
            {RANGES.map((option) => (
              <button key={option} type="button" aria-pressed={range === option} onClick={() => setRange(option)}>
                {option === 'ALL' ? 'All' : option}
              </button>
            ))}
          </div>
        </div>

        {shown === null ? (
          failed !== null ? (
            <div role="alert" className="flex flex-col items-start gap-2 p-3 text-sm">
              <p className="notice-error">{failed}</p>
              <button type="button" className="btn" onClick={() => setAttempt((current) => current + 1)}>
                Retry
              </button>
            </div>
          ) : (
            <div role="status" aria-label="Loading performance" className="space-y-3 p-3">
              <div className="skeleton h-8 w-2/3" />
              <div className="skeleton h-40" />
            </div>
          )
        ) : (
          <div className={`p-3 transition-opacity duration-200 ${refreshing ? 'opacity-50' : ''}`}>
            {hasCurve && (
              <dl className="grid grid-cols-2 gap-x-6 gap-y-3 sm:grid-cols-3">
                <Tile label="Change" sub={`since ${formatDay(shown.from)}`}>
                  <Change amount={shown.summary.change} percent={shown.summary.changePercent} />
                </Tile>
                <DayTile label="Best day" move={shown.summary.bestDay} />
                <DayTile label="Worst day" move={shown.summary.worstDay} />
              </dl>
            )}

            {hasCurve ? (
              <>
                {readout !== undefined && sinceStart !== null && (
                  <dl className="mt-3 flex flex-wrap items-baseline gap-x-4 gap-y-0.5 text-sm tabular-nums" aria-label="Value under the pointer, or the latest">
                    <div className="text-ink-3">
                      <dt className="sr-only">Day</dt>
                      <dd>{formatDay(readout.date)}</dd>
                    </div>
                    <div className="flex gap-1">
                      <dt className="text-ink-3">Value</dt>
                      <dd className="font-medium">{formatUsd(readout.value)}</dd>
                    </div>
                    <div className="flex gap-1">
                      <dt className="text-ink-3">Since start</dt>
                      <dd className="font-medium">
                        <Change amount={sinceStart.amount} percent={sinceStart.percent} />
                      </dd>
                    </div>
                  </dl>
                )}
                <div ref={containerRef} className="mt-1 h-56 w-full" role="img" aria-label={`Portfolio value from ${formatDay(shown.from)} to ${formatDay(shown.to)}`} />
              </>
            ) : (
              <p className="max-w-md text-sm text-ink-2">
                Your value will start moving once you hold something. Until then it is your cash: {formatUsd(shown.summary.endValue)}.
              </p>
            )}

            {shown.estimatedSymbols.length > 0 && (
              <p className="mt-2 max-w-xl text-xs text-ink-3">
                {shown.estimatedSymbols.join(', ')} {shown.estimatedSymbols.length === 1 ? 'is' : 'are'} valued at trade prices on the
                days no price history was available, so the curve is an estimate there.
              </p>
            )}

            {hasCurve && (
              <details className="mt-2 text-sm">
                <summary className="flex min-h-7 items-center text-ink-2 hover:text-ink pointer-coarse:min-h-11">Show the data as a table</summary>
                <div className="max-h-56 overflow-auto" tabIndex={0} role="region" aria-label="Performance data table">
                  <table className="grid-table">
                    <caption className="sr-only">Latest {Math.min(points.length, 40)} of {points.length} days, newest first</caption>
                    <thead>
                      <tr>
                        <th scope="col">Day</th>
                        <th scope="col" className="num">Value</th>
                        <th scope="col" className="num">Cash</th>
                        <th scope="col" className="num">In shares</th>
                      </tr>
                    </thead>
                    <tbody>
                      {points.slice(-40).reverse().map((point) => (
                        <tr key={point.date}>
                          <td>{formatDay(point.date)}</td>
                          <td className="num">{formatUsd(point.value)}</td>
                          <td className="num">{formatUsd(point.cash)}</td>
                          <td className="num">{formatUsd(point.invested)}</td>
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
    </>
  )
}

const cents = (amount: string) => Math.round(Number(amount) * 100)

/**
 * What each position has contributed since the account opened, as bars around a zero line: gains to
 * the right, losses to the left, every value written out with its sign and arrow.
 */
function PositionResults({ positions, dimmed }: { positions: PositionResult[] | null; dimmed: boolean }) {
  const total = positions?.reduce((sum, position) => sum + cents(position.totalPnl), 0) ?? 0
  const widest = Math.max(1, ...(positions ?? []).map((position) => Math.abs(cents(position.totalPnl))))
  return (
    <section aria-labelledby="results-heading" className="panel">
      <div className="panel-header">
        <h2 id="results-heading">Result by position</h2>
        <span className="normal-case tracking-normal">realized + unrealized</span>
      </div>
      {positions === null ? (
        <div role="status" aria-label="Loading" className="space-y-2 p-3">
          <div className="skeleton h-4" />
          <div className="skeleton h-4" />
        </div>
      ) : positions.length === 0 ? (
        <p className="max-w-xs p-3 text-sm text-ink-2">No positions yet. Each one you open or close is listed here with what it made or lost.</p>
      ) : (
        <div className={`transition-opacity duration-200 ${dimmed ? 'opacity-50' : ''}`}>
          <ul className="px-3">
            {positions.map((position) => {
              const value = cents(position.totalPnl)
              const share = (Math.abs(value) / widest) * 50 // half the track is one side of zero
              return (
                <li key={position.symbol} className="grid grid-cols-[4.5rem_minmax(0,1fr)_6.5rem] items-center gap-3 border-b border-rule py-1.5">
                  <span className="text-sm">
                    <span className="block font-semibold">{position.symbol}</span>
                    <span className="block text-xs text-ink-3">{position.open ? `${position.quantity} held` : 'closed'}</span>
                  </span>
                  <span aria-hidden className="relative h-2.5">
                    <span className="absolute inset-y-0 left-1/2 w-px bg-edge" />
                    <span
                      className={`absolute inset-y-0 ${value >= 0 ? 'left-1/2 rounded-r-control bg-up' : 'right-1/2 rounded-l-control bg-down'}`}
                      style={{ width: `${share}%` }}
                    />
                  </span>
                  <span className="text-right text-sm font-medium">
                    <Change amount={position.totalPnl} />
                  </span>
                </li>
              )
            })}
          </ul>
          <p className="flex items-baseline justify-between px-3 py-2 text-sm">
            <span className="text-ink-2">All positions</span>
            <span className="font-semibold">
              <Change amount={(total / 100).toFixed(2)} />
            </span>
          </p>
        </div>
      )}
    </section>
  )
}
