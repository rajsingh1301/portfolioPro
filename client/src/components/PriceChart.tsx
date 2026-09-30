import { CandlestickSeries, ColorType, HistogramSeries, LineSeries, LineStyle, createChart } from 'lightweight-charts'
import type { IChartApi, ISeriesApi, SeriesType, UTCTimestamp } from 'lightweight-charts'
import { useEffect, useMemo, useRef, useState } from 'react'

import { errorMessage } from '../api/client'
import { fetchCandles, fetchIndicators } from '../api/trading'
import { formatUsd } from '../lib/money'
import { CANDLE_RANGES } from '../types/trading'
import type { Candle, CandleRange, IndicatorPoint, Indicators } from '../types/trading'

interface PriceChartProps {
  symbol: string
}

/** The last request to finish, tagged with what it was for, so a stale one is never shown. */
type Loaded<T> = { key: string; data: T } | { key: string; error: string }

type Layer = 'sma20' | 'sma50' | 'ema20' | 'bollinger' | 'rsi' | 'macd'

/** `slot` is the categorical colour the layer is drawn in; its chip carries the same line-key. */
const LAYERS: { id: Layer; label: string; pane: boolean; slot: number | null }[] = [
  { id: 'sma20', label: 'SMA 20', pane: false, slot: 1 },
  { id: 'sma50', label: 'SMA 50', pane: false, slot: 2 },
  { id: 'ema20', label: 'EMA 20', pane: false, slot: 3 },
  { id: 'bollinger', label: 'Bollinger', pane: false, slot: null },
  { id: 'rsi', label: 'RSI', pane: true, slot: 1 },
  { id: 'macd', label: 'MACD', pane: true, slot: 1 },
]

const MAIN_HEIGHT = 300
const PANE_HEIGHT = 140

/** The design tokens, read once from CSS so the chart can never drift from the page. */
function chartColors() {
  const style = getComputedStyle(document.documentElement)
  const read = (name: string, fallback: string) => style.getPropertyValue(name).trim() || fallback
  return {
    paper: read('--color-paper', '#f7f4ec'),
    ink: read('--color-ink', '#16140f'),
    ink2: read('--color-ink-2', '#4e493f'),
    ink3: read('--color-ink-3', '#66615a'),
    rule: read('--color-rule', '#d8d1c1'),
    gain: read('--color-gain', '#006300'),
    loss: read('--color-loss', '#b3261e'),
    series: [1, 2, 3].map((n) => read(`--color-series-${n}`, ['#2a78d6', '#eb6834', '#1baf7a'][n - 1])),
  }
}

function withAlpha(hex: string, alpha: number): string {
  const value = hex.replace('#', '')
  const [r, g, b] = [0, 2, 4].map((i) => parseInt(value.slice(i, i + 2), 16))
  return `rgba(${r}, ${g}, ${b}, ${alpha})`
}

/** Prices arrive as strings; the chart library needs numbers, and this is display only. */
function toLine(points: IndicatorPoint[]) {
  return points.map((point) => ({ time: point.time as UTCTimestamp, value: Number(point.value) }))
}

function formatTime(seconds: number, intraday: boolean): string {
  const date = new Date(seconds * 1000)
  return intraday
    ? date.toLocaleString('en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false, timeZone: 'UTC' }) + ' UTC'
    : date.toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric', timeZone: 'UTC' })
}

export function PriceChart({ symbol }: PriceChartProps) {
  const [range, setRange] = useState<CandleRange>('1M')
  const [layers, setLayers] = useState<ReadonlySet<Layer>>(new Set())
  const [candles, setCandles] = useState<Loaded<Candle[]> | null>(null)
  const [indicators, setIndicators] = useState<Loaded<Indicators> | null>(null)
  const [hoverTime, setHoverTime] = useState<number | null>(null)
  const containerRef = useRef<HTMLDivElement>(null)
  const chartRef = useRef<IChartApi | null>(null)
  const seriesRef = useRef<ISeriesApi<'Candlestick'> | null>(null)
  const layerSeriesRef = useRef<ISeriesApi<SeriesType>[]>([])

  const key = `${symbol}:${range}`
  const intraday = range === '1D' || range === '1W'
  const wantsIndicators = layers.size > 0
  const panes = LAYERS.filter((layer) => layer.pane && layers.has(layer.id)).length

  // One chart for the life of the component; data is swapped in below.
  useEffect(() => {
    const container = containerRef.current
    if (container === null) {
      return
    }
    const c = chartColors()
    const chart = createChart(container, {
      autoSize: true,
      layout: {
        background: { type: ColorType.Solid, color: c.paper },
        textColor: c.ink2,
        fontFamily: "'IBM Plex Sans', system-ui, sans-serif",
        fontSize: 12,
        attributionLogo: false,
      },
      // Horizontal hairlines only, solid and one step off the paper: recessive, never dashed.
      grid: { vertLines: { visible: false }, horzLines: { color: c.rule } },
      rightPriceScale: { borderVisible: false },
      timeScale: { borderVisible: false, timeVisible: true },
      crosshair: {
        vertLine: { color: c.ink3, width: 1, style: LineStyle.Solid, labelBackgroundColor: c.ink },
        horzLine: { color: c.ink3, width: 1, style: LineStyle.Solid, labelBackgroundColor: c.ink },
      },
    })
    chartRef.current = chart
    // Rising candles are hollow and falling ones filled, so direction survives without colour.
    seriesRef.current = chart.addSeries(CandlestickSeries, {
      upColor: c.paper,
      downColor: c.loss,
      borderUpColor: c.gain,
      borderDownColor: c.loss,
      wickUpColor: c.gain,
      wickDownColor: c.loss,
      borderVisible: true,
    })
    const onMove = (param: { time?: unknown }) => setHoverTime(param.time === undefined ? null : Number(param.time))
    chart.subscribeCrosshairMove(onMove)
    return () => {
      chart.unsubscribeCrosshairMove(onMove)
      chart.remove()
      chartRef.current = null
      seriesRef.current = null
      layerSeriesRef.current = []
    }
  }, [])

  useEffect(() => {
    let active = true
    fetchCandles(symbol, range)
      .then((data) => {
        if (active) {
          setCandles({ key, data })
        }
      })
      .catch((failure: unknown) => {
        if (active) {
          setCandles({ key, error: errorMessage(failure, 'Could not load the chart') })
        }
      })
    return () => {
      active = false
    }
  }, [symbol, range, key])

  // Indicators are only requested once one is switched on, since the provider's daily
  // quota is small and most views never need them.
  useEffect(() => {
    if (!wantsIndicators) {
      return
    }
    let active = true
    fetchIndicators(symbol, range)
      .then((data) => {
        if (active) {
          setIndicators({ key, data })
        }
      })
      .catch((failure: unknown) => {
        if (active) {
          setIndicators({ key, error: errorMessage(failure, 'Could not load indicators') })
        }
      })
    return () => {
      active = false
    }
  }, [symbol, range, key, wantsIndicators])

  const currentCandles = candles !== null && candles.key === key ? candles : null
  // Switching range on the same stock keeps the old candles, dimmed, instead of blanking the
  // frame. A different stock never inherits them: that would be another company's chart.
  const heldCandles =
    currentCandles === null && candles !== null && candles.key.split(':')[0] === symbol ? candles : null
  const shownCandles = currentCandles ?? heldCandles
  const refreshing = currentCandles === null && heldCandles !== null
  const currentIndicators = indicators !== null && indicators.key === key ? indicators : null

  useEffect(() => {
    const series = seriesRef.current
    if (series === null || shownCandles === null || 'error' in shownCandles) {
      return
    }
    series.setData(
      shownCandles.data.map((candle) => ({
        time: candle.time as UTCTimestamp,
        open: Number(candle.open),
        high: Number(candle.high),
        low: Number(candle.low),
        close: Number(candle.close),
      })),
    )
    chartRef.current?.timeScale().fitContent()
  }, [shownCandles])

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
    const line = (points: IndicatorPoint[], color: string, pane = 0, width: 1 | 2 = 2) => {
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
      line(data.bollinger.upper, c.ink3, 0, 1)
      line(data.bollinger.lower, c.ink3, 0, 1)
    }
    let pane = 1
    if (layers.has('rsi')) {
      const rsi = line(data.rsi14, c.series[0], pane)
      for (const level of [70, 30]) {
        rsi.createPriceLine({ price: level, color: c.ink3, lineWidth: 1, lineStyle: LineStyle.Dotted, axisLabelVisible: true, title: '' })
      }
      pane += 1
    }
    if (layers.has('macd')) {
      const histogram = chart.addSeries(HistogramSeries, { lastValueVisible: false, priceLineVisible: false }, pane)
      histogram.setData(
        data.macd.histogram.map((point) => ({
          time: point.time as UTCTimestamp,
          value: Number(point.value),
          color: withAlpha(Number(point.value) >= 0 ? c.gain : c.loss, 0.4),
        })),
      )
      added.push(histogram)
      line(data.macd.line, c.series[0], pane)
      line(data.macd.signal, c.series[1], pane)
    }
    layerSeriesRef.current = added
  }, [currentIndicators, layers])

  function toggle(layer: Layer) {
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

  const rows = useMemo(
    () => (shownCandles !== null && 'data' in shownCandles ? shownCandles.data : []),
    [shownCandles],
  )
  const byTime = useMemo(() => new Map(rows.map((candle) => [candle.time, candle])), [rows])
  const readout = (hoverTime !== null ? byTime.get(hoverTime) : undefined) ?? rows[rows.length - 1]

  let overlay: string | null = null
  if (shownCandles === null) {
    overlay = 'Loading chart…'
  } else if ('error' in shownCandles) {
    overlay = shownCandles.error
  } else if (shownCandles.data.length === 0) {
    overlay = 'No chart data for this range'
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
    <div className="mt-8">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h3 className="text-lg">{symbol} price history</h3>
        <div className="seg seg-sm" role="group" aria-label="Range">
          {CANDLE_RANGES.map((option) => (
            <button key={option} type="button" aria-pressed={range === option} onClick={() => setRange(option)}>
              {option}
            </button>
          ))}
        </div>
      </div>

      <div className="mt-3 flex flex-wrap gap-2" role="group" aria-label="Indicators">
        {LAYERS.map((layer) => (
          <button key={layer.id} type="button" className="chip" aria-pressed={layers.has(layer.id)} onClick={() => toggle(layer.id)}>
            <span
              aria-hidden
              className="h-0.5 w-4"
              style={{ backgroundColor: layer.slot === null ? 'var(--color-ink-3)' : `var(--color-series-${layer.slot})` }}
            />
            {layer.label}
          </button>
        ))}
      </div>

      {readout !== undefined && (
        <dl className="mt-4 flex flex-wrap items-baseline gap-x-5 gap-y-1 text-sm tabular-nums" aria-label="Candle under the pointer, or the latest">
          <div className="text-ink-3">
            <dt className="sr-only">Time</dt>
            <dd>{formatTime(readout.time, intraday)}</dd>
          </div>
          {(
            [
              ['O', readout.open],
              ['H', readout.high],
              ['L', readout.low],
              ['C', readout.close],
            ] as const
          ).map(([label, value]) => (
            <div key={label} className="flex gap-1.5">
              <dt className="text-ink-3">{label}</dt>
              <dd className="font-medium">{formatUsd(value)}</dd>
            </div>
          ))}
        </dl>
      )}

      <div
        className={`relative mt-2 transition-opacity duration-200 ${refreshing ? 'opacity-50' : ''}`}
        style={{ height: MAIN_HEIGHT + panes * PANE_HEIGHT }}
      >
        <div ref={containerRef} className="h-full w-full" />
        {overlay !== null && (
          <p
            role={shownCandles !== null && 'error' in shownCandles ? 'alert' : 'status'}
            className="absolute inset-0 flex items-center justify-center bg-paper/85 px-6 text-center text-sm text-ink-2"
          >
            {overlay}
          </p>
        )}
      </div>
      {refreshing && (
        <p role="status" className="sr-only">
          Updating the chart
        </p>
      )}

      {indicatorNote !== null && <p className="mt-2 text-xs text-ink-3">{indicatorNote}</p>}
      {indicatorData !== null && wantsIndicators && (
        <section aria-label="Indicator readings" className="mt-6 border-t border-ink pt-3">
          <ul>
            {indicatorData.readings.map((reading) => (
              <li key={reading.indicator} className="flex flex-col gap-0.5 border-b border-rule py-2 text-sm sm:flex-row sm:gap-4">
                <span className="text-ink-3 sm:w-52 sm:shrink-0">{reading.indicator}</span>
                <span>{reading.label}</span>
              </li>
            ))}
          </ul>
          <p className="mt-3 text-xs text-ink-3">{indicatorData.disclaimer}</p>
        </section>
      )}

      {rows.length > 0 && (
        <details className="mt-4 text-sm">
          <summary className="flex min-h-11 items-center text-ink-2 hover:text-ink">Show the data as a table</summary>
          {/* Scrolls, so a keyboard user must be able to reach it to scroll it. */}
          <div className="max-h-72 overflow-auto" tabIndex={0} role="region" aria-label="Candle data table">
            <table className="data-table">
              <caption className="pb-2 text-left text-xs text-ink-3">
                Latest {latest.length} of {rows.length} candles, newest first
              </caption>
              <thead>
                <tr>
                  <th scope="col">Time</th>
                  <th scope="col" className="num">Open</th>
                  <th scope="col" className="num">High</th>
                  <th scope="col" className="num">Low</th>
                  <th scope="col" className="num">Close</th>
                  <th scope="col" className="num hidden sm:table-cell">Volume</th>
                </tr>
              </thead>
              <tbody>
                {latest.map((candle) => (
                  <tr key={candle.time}>
                    <td className="whitespace-nowrap">{formatTime(candle.time, intraday)}</td>
                    <td className="num">{formatUsd(candle.open)}</td>
                    <td className="num">{formatUsd(candle.high)}</td>
                    <td className="num">{formatUsd(candle.low)}</td>
                    <td className="num">{formatUsd(candle.close)}</td>
                    <td className="num hidden sm:table-cell">{candle.volume.toLocaleString('en-US')}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </details>
      )}
    </div>
  )
}
