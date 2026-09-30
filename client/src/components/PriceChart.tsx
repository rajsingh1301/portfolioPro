import { CandlestickSeries, ColorType, HistogramSeries, LineSeries, LineStyle, createChart } from 'lightweight-charts'
import type { IChartApi, ISeriesApi, SeriesType, UTCTimestamp } from 'lightweight-charts'
import { useEffect, useRef, useState } from 'react'

import { errorMessage } from '../api/client'
import { fetchCandles, fetchIndicators } from '../api/trading'
import { CANDLE_RANGES } from '../types/trading'
import type { Candle, CandleRange, IndicatorPoint, Indicators } from '../types/trading'

interface PriceChartProps {
  symbol: string
}

/** The last request to finish, tagged with what it was for, so a stale one is never shown. */
type Loaded<T> = { key: string; data: T } | { key: string; error: string }

type Layer = 'sma20' | 'sma50' | 'ema20' | 'bollinger' | 'rsi' | 'macd'

const LAYERS: { id: Layer; label: string; pane: boolean }[] = [
  { id: 'sma20', label: 'SMA 20', pane: false },
  { id: 'sma50', label: 'SMA 50', pane: false },
  { id: 'ema20', label: 'EMA 20', pane: false },
  { id: 'bollinger', label: 'Bollinger', pane: false },
  { id: 'rsi', label: 'RSI', pane: true },
  { id: 'macd', label: 'MACD', pane: true },
]

const MAIN_HEIGHT = 288
const PANE_HEIGHT = 130

/** Prices arrive as strings; the chart library needs numbers, and this is display only. */
function toLine(points: IndicatorPoint[]) {
  return points.map((point) => ({ time: point.time as UTCTimestamp, value: Number(point.value) }))
}

export function PriceChart({ symbol }: PriceChartProps) {
  const [range, setRange] = useState<CandleRange>('1M')
  const [layers, setLayers] = useState<ReadonlySet<Layer>>(new Set())
  const [candles, setCandles] = useState<Loaded<Candle[]> | null>(null)
  const [indicators, setIndicators] = useState<Loaded<Indicators> | null>(null)
  const containerRef = useRef<HTMLDivElement>(null)
  const chartRef = useRef<IChartApi | null>(null)
  const seriesRef = useRef<ISeriesApi<'Candlestick'> | null>(null)
  const layerSeriesRef = useRef<ISeriesApi<SeriesType>[]>([])

  const key = `${symbol}:${range}`
  const wantsIndicators = layers.size > 0
  const panes = LAYERS.filter((layer) => layer.pane && layers.has(layer.id)).length

  // One chart for the life of the component; data is swapped in below.
  useEffect(() => {
    const container = containerRef.current
    if (container === null) {
      return
    }
    const chart = createChart(container, {
      autoSize: true,
      layout: { background: { type: ColorType.Solid, color: '#ffffff' }, textColor: '#475569' },
      grid: { vertLines: { color: '#f1f5f9' }, horzLines: { color: '#f1f5f9' } },
      rightPriceScale: { borderColor: '#e2e8f0' },
      timeScale: { borderColor: '#e2e8f0', timeVisible: true },
    })
    chartRef.current = chart
    seriesRef.current = chart.addSeries(CandlestickSeries, {
      upColor: '#15803d',
      downColor: '#dc2626',
      wickUpColor: '#15803d',
      wickDownColor: '#dc2626',
      borderVisible: false,
    })
    return () => {
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
  const currentIndicators = indicators !== null && indicators.key === key ? indicators : null

  useEffect(() => {
    const series = seriesRef.current
    if (series === null || currentCandles === null || 'error' in currentCandles) {
      return
    }
    series.setData(
      currentCandles.data.map((candle) => ({
        time: candle.time as UTCTimestamp,
        open: Number(candle.open),
        high: Number(candle.high),
        low: Number(candle.low),
        close: Number(candle.close),
      })),
    )
    chartRef.current?.timeScale().fitContent()
  }, [currentCandles])

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
    const added: ISeriesApi<SeriesType>[] = []
    const line = (points: IndicatorPoint[], color: string, pane = 0, dashed = false) => {
      const series = chart.addSeries(
        LineSeries,
        {
          color,
          lineWidth: 1,
          lastValueVisible: false,
          priceLineVisible: false,
          crosshairMarkerVisible: false,
          lineStyle: dashed ? LineStyle.Dashed : LineStyle.Solid,
        },
        pane,
      )
      series.setData(toLine(points))
      added.push(series)
      return series
    }

    if (layers.has('sma20')) line(data.sma20, '#2563eb')
    if (layers.has('sma50')) line(data.sma50, '#f59e0b')
    if (layers.has('ema20')) line(data.ema20, '#8b5cf6')
    if (layers.has('bollinger')) {
      line(data.bollinger.upper, '#94a3b8', 0, true)
      line(data.bollinger.middle, '#cbd5e1')
      line(data.bollinger.lower, '#94a3b8', 0, true)
    }
    let pane = 1
    if (layers.has('rsi')) {
      const rsi = line(data.rsi14, '#0ea5e9', pane)
      for (const level of [70, 30]) {
        rsi.createPriceLine({ price: level, color: '#cbd5e1', lineWidth: 1, lineStyle: LineStyle.Dotted, axisLabelVisible: true, title: '' })
      }
      pane += 1
    }
    if (layers.has('macd')) {
      const histogram = chart.addSeries(HistogramSeries, { lastValueVisible: false, priceLineVisible: false }, pane)
      histogram.setData(
        data.macd.histogram.map((point) => ({
          time: point.time as UTCTimestamp,
          value: Number(point.value),
          color: Number(point.value) >= 0 ? '#86efac' : '#fca5a5',
        })),
      )
      added.push(histogram)
      line(data.macd.line, '#2563eb', pane)
      line(data.macd.signal, '#f59e0b', pane)
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

  let overlay: string | null = null
  if (currentCandles === null) {
    overlay = 'Loading chart…'
  } else if ('error' in currentCandles) {
    overlay = currentCandles.error
  } else if (currentCandles.data.length === 0) {
    overlay = 'No chart data for this range'
  }
  const indicatorNote =
    wantsIndicators && currentIndicators === null
      ? 'Loading indicators…'
      : currentIndicators !== null && 'error' in currentIndicators
        ? currentIndicators.error
        : null
  const readings = currentIndicators !== null && 'data' in currentIndicators ? currentIndicators.data : null

  return (
    <div className="mt-5 border-t border-slate-100 pt-5">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-medium text-slate-500">{symbol} price history</h3>
        <div className="inline-flex overflow-hidden rounded-md border border-slate-300" role="group" aria-label="Range">
          {CANDLE_RANGES.map((option) => (
            <button
              key={option}
              type="button"
              aria-pressed={range === option}
              onClick={() => setRange(option)}
              className={`px-2.5 py-1 text-xs font-medium ${
                range === option ? 'bg-slate-900 text-white' : 'bg-white text-slate-700 hover:bg-slate-50'
              }`}
            >
              {option}
            </button>
          ))}
        </div>
      </div>

      <div className="mt-3 flex flex-wrap gap-2" role="group" aria-label="Indicators">
        {LAYERS.map((layer) => (
          <button
            key={layer.id}
            type="button"
            aria-pressed={layers.has(layer.id)}
            onClick={() => toggle(layer.id)}
            className={`rounded-full border px-2.5 py-0.5 text-xs font-medium ${
              layers.has(layer.id)
                ? 'border-slate-900 bg-slate-900 text-white'
                : 'border-slate-300 bg-white text-slate-700 hover:bg-slate-50'
            }`}
          >
            {layer.label}
          </button>
        ))}
      </div>

      <div className="relative mt-3" style={{ height: MAIN_HEIGHT + panes * PANE_HEIGHT }}>
        <div ref={containerRef} className="h-full w-full" />
        {overlay !== null && (
          <p
            role={currentCandles !== null && 'error' in currentCandles ? 'alert' : 'status'}
            className="absolute inset-0 flex items-center justify-center bg-white/80 px-6 text-center text-sm text-slate-500"
          >
            {overlay}
          </p>
        )}
      </div>

      {indicatorNote !== null && <p className="mt-2 text-xs text-slate-500">{indicatorNote}</p>}
      {readings !== null && wantsIndicators && (
        <div className="mt-3 rounded-md bg-slate-50 p-3">
          <ul className="space-y-1 text-sm text-slate-700">
            {readings.readings.map((reading) => (
              <li key={reading.indicator}>
                <span className="font-medium text-slate-900">{reading.indicator}:</span> {reading.label}
              </li>
            ))}
          </ul>
          <p className="mt-2 text-xs text-slate-500">{readings.disclaimer}</p>
        </div>
      )}
    </div>
  )
}
