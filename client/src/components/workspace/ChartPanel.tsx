import { CandlestickSeries, HistogramSeries, LineSeries, LineStyle, createChart } from 'lightweight-charts'
import type { IChartApi, ISeriesApi, SeriesType, UTCTimestamp } from 'lightweight-charts'
import { useEffect, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'

import { errorMessage } from '../../api/client'
import { fetchCandles, fetchIndicators } from '../../api/trading'
import { useTheme } from '../../context/useTheme'
import { useTradingData } from '../../data/useTradingData'
import { baseChartOptions, chartColors, withAlpha } from '../../lib/chartTheme'
import { changeBetween, formatCompactNumber, formatUsd } from '../../lib/money'
import type { useQuote } from '../../lib/useQuote'
import { CANDLE_RANGES } from '../../types/trading'
import type { Candle, CandleRange, IndicatorPoint, Indicators } from '../../types/trading'
import { Change } from '../ui/Change'
import { PriceCell } from '../ui/PriceCell'
import { FundamentalsStrip } from './FundamentalsStrip'

/** The last request to finish, tagged with what it was for, so a stale one is never shown. */
type Loaded<T> = { key: string; data: T } | { key: string; error: string }

type Layer = 'sma20' | 'sma50' | 'ema20' | 'bollinger' | 'rsi' | 'macd'

/** `slot` is the categorical colour the layer is drawn in; its checkbox carries the same line-key. */
const LAYERS: { id: Layer; label: string; pane: boolean; slot: number | null }[] = [
  { id: 'sma20', label: 'SMA 20', pane: false, slot: 1 },
  { id: 'sma50', label: 'SMA 50', pane: false, slot: 2 },
  { id: 'ema20', label: 'EMA 20', pane: false, slot: 3 },
  { id: 'bollinger', label: 'Bollinger Bands', pane: false, slot: null },
  { id: 'rsi', label: 'RSI 14', pane: true, slot: 1 },
  { id: 'macd', label: 'MACD', pane: true, slot: 1 },
]

const PANE_HEIGHT = 110

function toLine(points: IndicatorPoint[]) {
  return points.map((point) => ({ time: point.time as UTCTimestamp, value: Number(point.value) }))
}

function formatTime(seconds: number, intraday: boolean): string {
  const date = new Date(seconds * 1000)
  return intraday
    ? `${date.toLocaleString('en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false, timeZone: 'UTC' })} UTC`
    : date.toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric', timeZone: 'UTC' })
}

interface ChartPanelProps {
  symbol: string
  quote: ReturnType<typeof useQuote>
  /** The full-page version: fundamentals above the chart and a data table below it. */
  expanded?: boolean
  /** Extra buttons for the header, e.g. showing and hiding the side panels. */
  actions?: ReactNode
}

/**
 * The candlestick chart, with volume along its foot, the price and change in the header, and the
 * open, high, low, close and volume of whichever candle the pointer is on. Timeframes and
 * indicators are one click away; an indicator can be a line over the price or a pane of its own.
 */
export function ChartPanel({ symbol, quote, expanded = false, actions }: ChartPanelProps) {
  const { theme } = useTheme()
  const { watchlist, toggleWatch } = useTradingData()
  const [range, setRange] = useState<CandleRange>('1M')
  const [layers, setLayers] = useState<ReadonlySet<Layer>>(new Set())
  const [candles, setCandles] = useState<Loaded<Candle[]> | null>(null)
  const [indicators, setIndicators] = useState<Loaded<Indicators> | null>(null)
  const [attempt, setAttempt] = useState(0)
  const [hoverTime, setHoverTime] = useState<number | null>(null)
  const [watchError, setWatchError] = useState<string | null>(null)
  const containerRef = useRef<HTMLDivElement>(null)
  const chartRef = useRef<IChartApi | null>(null)
  const candleRef = useRef<ISeriesApi<'Candlestick'> | null>(null)
  const volumeRef = useRef<ISeriesApi<'Histogram'> | null>(null)
  const layerSeriesRef = useRef<ISeriesApi<SeriesType>[]>([])

  const key = `${symbol}:${range}:${attempt}`
  const intraday = range === '1D' || range === '1W'
  const wantsIndicators = layers.size > 0
  const watched = watchlist.data?.some((item) => item.symbol === symbol) ?? false

  // One chart for the life of the component; data and colours are swapped in below.
  useEffect(() => {
    const container = containerRef.current
    if (container === null) {
      return
    }
    const c = chartColors()
    const base = baseChartOptions(c)
    const chart = createChart(container, { ...base, timeScale: { ...base.timeScale, timeVisible: true } })
    chartRef.current = chart
    const candle = chart.addSeries(CandlestickSeries, {
      upColor: c.up,
      downColor: c.down,
      borderUpColor: c.up,
      borderDownColor: c.down,
      wickUpColor: c.up,
      wickDownColor: c.down,
    })
    // Candles keep the upper part of the pane; volume sits along the foot, on a scale of its own.
    candle.priceScale().applyOptions({ scaleMargins: { top: 0.06, bottom: 0.24 } })
    const volume = chart.addSeries(HistogramSeries, {
      priceFormat: { type: 'volume' },
      priceScaleId: '',
      lastValueVisible: false,
      priceLineVisible: false,
    })
    volume.priceScale().applyOptions({ scaleMargins: { top: 0.82, bottom: 0 } })
    candleRef.current = candle
    volumeRef.current = volume

    const onMove = (param: { time?: unknown }) => setHoverTime(param.time === undefined ? null : Number(param.time))
    chart.subscribeCrosshairMove(onMove)
    return () => {
      chart.unsubscribeCrosshairMove(onMove)
      chart.remove()
      chartRef.current = null
      candleRef.current = null
      volumeRef.current = null
      layerSeriesRef.current = []
    }
  }, [])

  // Switching theme restyles the chart in place, from the CSS variables the switch has just set.
  useEffect(() => {
    const chart = chartRef.current
    if (chart === null) {
      return
    }
    const c = chartColors()
    chart.applyOptions(baseChartOptions(c))
    candleRef.current?.applyOptions({
      upColor: c.up,
      downColor: c.down,
      borderUpColor: c.up,
      borderDownColor: c.down,
      wickUpColor: c.up,
      wickDownColor: c.down,
    })
  }, [theme])

  useEffect(() => {
    let active = true
    fetchCandles(symbol, range)
      .then((data) => active && setCandles({ key, data }))
      .catch((failure: unknown) => active && setCandles({ key, error: errorMessage(failure, 'Could not load the chart') }))
    return () => {
      active = false
    }
  }, [symbol, range, key])

  // Indicators are only requested once one is switched on, since the candle provider's daily
  // quota is small and most views never need them.
  useEffect(() => {
    if (!wantsIndicators) {
      return
    }
    let active = true
    fetchIndicators(symbol, range)
      .then((data) => active && setIndicators({ key, data }))
      .catch((failure: unknown) => active && setIndicators({ key, error: errorMessage(failure, 'Could not load indicators') }))
    return () => {
      active = false
    }
  }, [symbol, range, key, wantsIndicators])

  const currentCandles = candles !== null && candles.key === key ? candles : null
  // A new range on the same stock keeps the old candles, dimmed, instead of blanking the frame.
  // A different stock never inherits them: that would be another company's chart.
  const heldCandles =
    currentCandles === null && candles !== null && candles.key.split(':')[0] === symbol ? candles : null
  const shownCandles = currentCandles ?? heldCandles
  const refreshing = currentCandles === null && heldCandles !== null
  const currentIndicators = indicators !== null && indicators.key === key ? indicators : null

  const rows = useMemo(
    () => (shownCandles !== null && 'data' in shownCandles ? shownCandles.data : []),
    [shownCandles],
  )

  useEffect(() => {
    const candle = candleRef.current
    const volume = volumeRef.current
    if (candle === null || volume === null || shownCandles === null || 'error' in shownCandles) {
      return
    }
    const c = chartColors()
    candle.setData(
      shownCandles.data.map((row) => ({
        time: row.time as UTCTimestamp,
        open: Number(row.open),
        high: Number(row.high),
        low: Number(row.low),
        close: Number(row.close),
      })),
    )
    volume.setData(
      shownCandles.data.map((row) => ({
        time: row.time as UTCTimestamp,
        value: row.volume,
        color: withAlpha(Number(row.close) >= Number(row.open) ? c.up : c.down, 0.4),
      })),
    )
    chartRef.current?.timeScale().fitContent()
    // The volume colours are baked into the data, so a theme switch has to rebuild them.
  }, [shownCandles, theme])

  // Overlays share the price scale; RSI and MACD each get a pane of their own below it.
  useEffect(() => {
    const chart = chartRef.current
    if (chart === null) {
      return
    }
    for (const series of layerSeriesRef.current) {
      chart.removeSeries(series)
    }
    layerSeriesRef.current = []
    if (currentIndicators === null || 'error' in currentIndicators) {
      return
    }
    const data = currentIndicators.data
    const c = chartColors()
    const added: ISeriesApi<SeriesType>[] = []
    const line = (points: IndicatorPoint[], color: string, pane = 0, width: 1 | 2 = 1) => {
      const series = chart.addSeries(
        LineSeries,
        { color, lineWidth: width, lastValueVisible: false, priceLineVisible: false, crosshairMarkerVisible: false },
        pane,
      )
      series.setData(toLine(points))
      added.push(series)
      return series
    }

    if (layers.has('sma20')) line(data.sma20, c.series[0])
    if (layers.has('sma50')) line(data.sma50, c.series[1])
    if (layers.has('ema20')) line(data.ema20, c.series[2])
    if (layers.has('bollinger')) {
      line(data.bollinger.upper, c.ink3)
      line(data.bollinger.lower, c.ink3)
    }
    let pane = 1
    if (layers.has('rsi')) {
      const rsi = line(data.rsi14, c.series[0], pane)
      for (const level of [70, 30]) {
        rsi.createPriceLine({ price: level, color: c.ink3, lineWidth: 1, lineStyle: LineStyle.Dotted, axisLabelVisible: true, title: '' })
      }
      chart.panes()[pane]?.setHeight(PANE_HEIGHT)
      pane += 1
    }
    if (layers.has('macd')) {
      const histogram = chart.addSeries(HistogramSeries, { lastValueVisible: false, priceLineVisible: false }, pane)
      histogram.setData(
        data.macd.histogram.map((point) => ({
          time: point.time as UTCTimestamp,
          value: Number(point.value),
          color: withAlpha(Number(point.value) >= 0 ? c.up : c.down, 0.5),
        })),
      )
      added.push(histogram)
      line(data.macd.line, c.series[0], pane)
      line(data.macd.signal, c.series[1], pane)
      chart.panes()[pane]?.setHeight(PANE_HEIGHT)
    }
    layerSeriesRef.current = added
  }, [currentIndicators, layers, theme])

  function toggleLayer(layer: Layer) {
    setLayers((current) => {
      const next = new Set(current)
      if (next.has(layer)) {
        next.delete(layer)
      } else {
        next.add(layer)
      }
      return next
    })
  }

  async function handleWatch() {
    setWatchError(await toggleWatch(symbol, watched))
  }

  const indexByTime = useMemo(() => new Map(rows.map((row, position) => [row.time, position])), [rows])
  const hoveredIndex = hoverTime !== null ? indexByTime.get(hoverTime) : undefined
  const readoutIndex = hoveredIndex ?? rows.length - 1
  const readout = rows[readoutIndex]
  const before = readoutIndex > 0 ? rows[readoutIndex - 1] : undefined
  const candleChange = readout !== undefined && before !== undefined ? changeBetween(before.close, readout.close) : null

  let overlay: ReactNode = null
  if (shownCandles === null) {
    overlay = <p role="status" className="text-sm text-ink-3">Loading chart…</p>
  } else if ('error' in shownCandles) {
    overlay = (
      <div role="alert" className="flex flex-col items-center gap-2">
        <p className="notice-error">{shownCandles.error}</p>
        <button type="button" className="btn" onClick={() => setAttempt((current) => current + 1)}>
          Retry
        </button>
      </div>
    )
  } else if (shownCandles.data.length === 0) {
    overlay = <p className="text-sm text-ink-3">No chart data for this range</p>
  }
  const indicatorNote =
    wantsIndicators && currentIndicators === null
      ? 'Loading indicators…'
      : currentIndicators !== null && 'error' in currentIndicators
        ? currentIndicators.error
        : null
  const indicatorData = currentIndicators !== null && 'data' in currentIndicators ? currentIndicators.data : null
  const latest = rows.slice(-40).reverse()

  return (
    <section aria-label={`${symbol} chart`} className="panel h-full">
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1 border-b border-rule px-2.5 py-1.5">
        <h2 className="text-lg font-semibold">{symbol}</h2>
        {quote.quote !== null ? (
          <>
            <span data-testid="quote-price">
              <PriceCell value={quote.quote.price} className="text-lg font-semibold" />
            </span>
            <Change amount={quote.quote.change ?? undefined} percent={quote.quote.percentChange ?? undefined} className="text-sm" />
          </>
        ) : quote.error !== null ? (
          <span className="flex items-center gap-2 text-sm text-down">
            {quote.error}
            <button type="button" className="btn btn-sm" onClick={quote.retry}>Retry</button>
          </span>
        ) : (
          <span className="skeleton h-5 w-28" role="status" aria-label="Loading price" />
        )}

        <div className="ml-auto flex flex-wrap items-center gap-2">
          <div className="seg" role="group" aria-label="Range">
            {CANDLE_RANGES.map((option) => (
              <button key={option} type="button" aria-pressed={range === option} onClick={() => setRange(option)}>
                {option}
              </button>
            ))}
          </div>

          <details className="relative">
            <summary className="btn list-none [&::-webkit-details-marker]:hidden">Indicators{wantsIndicators ? ` (${layers.size})` : ''}</summary>
            <div role="group" aria-label="Indicators" className="absolute right-0 top-full z-20 mt-1 w-48 rounded-control border border-edge bg-panel p-1">
              {LAYERS.map((layer) => (
                <label key={layer.id} className="flex min-h-7 items-center gap-2 rounded-control px-2 text-sm hover:bg-hover pointer-coarse:min-h-11">
                  <input type="checkbox" checked={layers.has(layer.id)} onChange={() => toggleLayer(layer.id)} className="size-3.5 accent-accent" />
                  <span aria-hidden className="h-0.5 w-3.5 shrink-0" style={{ backgroundColor: layer.slot === null ? 'var(--color-ink-3)' : `var(--color-series-${layer.slot})` }} />
                  {layer.label}
                </label>
              ))}
            </div>
          </details>

          <button type="button" className="btn" onClick={() => void handleWatch()} aria-pressed={watched}>
            {watched ? 'Unwatch' : 'Watch'}
          </button>
          {actions}
        </div>
        {watchError !== null && <p role="alert" className="notice-error w-full">{watchError}</p>}
      </div>

      {expanded && <FundamentalsStrip symbol={symbol} />}

      <div className={`relative min-h-0 flex-1 transition-opacity duration-200 ${refreshing ? 'opacity-50' : ''}`}>
        <div ref={containerRef} className="absolute inset-0" role="img" aria-label={`${symbol} candlestick chart, ${range}`} />
        {readout !== undefined && (
          <dl
            aria-label="Candle under the pointer, or the latest"
            className="pointer-events-none absolute left-2 top-1.5 z-[2] flex flex-wrap items-baseline gap-x-3 gap-y-0.5 text-sm tabular-nums"
          >
            <div className="text-ink-3">
              <dt className="sr-only">Time</dt>
              <dd>{formatTime(readout.time, intraday)}</dd>
            </div>
            {(['open', 'high', 'low', 'close'] as const).map((field) => (
              <div key={field} className="flex gap-1">
                <dt className="text-ink-3">{field.slice(0, 1).toUpperCase()}</dt>
                <dd className="font-medium">{formatUsd(readout[field])}</dd>
              </div>
            ))}
            <div className="flex gap-1">
              <dt className="text-ink-3">Vol</dt>
              <dd className="font-medium">{formatCompactNumber(readout.volume)}</dd>
            </div>
            {candleChange !== null && (
              <div>
                <dt className="sr-only">Change from the previous candle</dt>
                <dd>
                  <Change amount={candleChange.amount} percent={candleChange.percent} />
                </dd>
              </div>
            )}
          </dl>
        )}
        {overlay !== null && (
          <div className="absolute inset-0 z-[3] flex items-center justify-center bg-panel/85 px-6 text-center">{overlay}</div>
        )}
      </div>

      {(indicatorNote !== null || (indicatorData !== null && wantsIndicators)) && (
        <div className="max-h-28 shrink-0 overflow-auto border-t border-rule px-2.5 py-1.5 text-sm">
          {indicatorNote !== null && <p className="text-ink-3">{indicatorNote}</p>}
          {indicatorData !== null && wantsIndicators && (
            <section aria-label="Indicator readings">
              <ul className="grid gap-x-6 sm:grid-cols-2">
                {indicatorData.readings.map((reading) => (
                  <li key={reading.indicator} className="flex gap-2">
                    <span className="shrink-0 text-ink-3">{reading.indicator}</span>
                    <span>{reading.label}</span>
                  </li>
                ))}
              </ul>
              <p className="mt-1 text-xs text-ink-3">{indicatorData.disclaimer}</p>
            </section>
          )}
        </div>
      )}

      {expanded && rows.length > 0 && (
        <details className="shrink-0 border-t border-rule text-sm">
          <summary className="flex min-h-8 items-center px-2.5 text-ink-2 hover:text-ink pointer-coarse:min-h-11">Show the data as a table</summary>
          <div className="max-h-56 overflow-auto" tabIndex={0} role="region" aria-label="Candle data table">
            <table className="grid-table">
              <caption className="sr-only">
                Latest {latest.length} of {rows.length} candles, newest first
              </caption>
              <thead>
                <tr>
                  <th scope="col">Time</th>
                  <th scope="col" className="num">Open</th>
                  <th scope="col" className="num">High</th>
                  <th scope="col" className="num">Low</th>
                  <th scope="col" className="num">Close</th>
                  <th scope="col" className="num">Volume</th>
                </tr>
              </thead>
              <tbody>
                {latest.map((candle) => (
                  <tr key={candle.time}>
                    <td>{formatTime(candle.time, intraday)}</td>
                    <td className="num">{formatUsd(candle.open)}</td>
                    <td className="num">{formatUsd(candle.high)}</td>
                    <td className="num">{formatUsd(candle.low)}</td>
                    <td className="num">{formatUsd(candle.close)}</td>
                    <td className="num">{candle.volume.toLocaleString('en-US')}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </details>
      )}
    </section>
  )
}
