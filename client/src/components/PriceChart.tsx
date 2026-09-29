import { CandlestickSeries, ColorType, createChart } from 'lightweight-charts'
import type { IChartApi, ISeriesApi, UTCTimestamp } from 'lightweight-charts'
import { useEffect, useRef, useState } from 'react'

import { errorMessage } from '../api/client'
import { fetchCandles } from '../api/trading'
import { CANDLE_RANGES } from '../types/trading'
import type { Candle, CandleRange } from '../types/trading'

interface PriceChartProps {
  symbol: string
}

/** The last request to finish, tagged with what it was for, so a stale one is never shown. */
type Loaded = { key: string; candles: Candle[] } | { key: string; error: string }

export function PriceChart({ symbol }: PriceChartProps) {
  const [range, setRange] = useState<CandleRange>('1M')
  const [loaded, setLoaded] = useState<Loaded | null>(null)
  const containerRef = useRef<HTMLDivElement>(null)
  const chartRef = useRef<IChartApi | null>(null)
  const seriesRef = useRef<ISeriesApi<'Candlestick'> | null>(null)

  const key = `${symbol}:${range}`

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
    }
  }, [])

  useEffect(() => {
    let active = true
    fetchCandles(symbol, range)
      .then((candles) => {
        if (active) {
          setLoaded({ key, candles })
        }
      })
      .catch((failure: unknown) => {
        if (active) {
          setLoaded({ key, error: errorMessage(failure, 'Could not load the chart') })
        }
      })
    return () => {
      active = false
    }
  }, [symbol, range, key])

  const current = loaded !== null && loaded.key === key ? loaded : null

  useEffect(() => {
    const series = seriesRef.current
    if (series === null || current === null || 'error' in current) {
      return
    }
    // Prices are strings everywhere else; the chart library needs numbers, and this is
    // display only, so it is the one place the conversion happens.
    series.setData(
      current.candles.map((candle) => ({
        time: candle.time as UTCTimestamp,
        open: Number(candle.open),
        high: Number(candle.high),
        low: Number(candle.low),
        close: Number(candle.close),
      })),
    )
    chartRef.current?.timeScale().fitContent()
  }, [current])

  let overlay: string | null = null
  if (current === null) {
    overlay = 'Loading chart…'
  } else if ('error' in current) {
    overlay = current.error
  } else if (current.candles.length === 0) {
    overlay = 'No chart data for this range'
  }

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
      <div className="relative mt-3 h-72">
        <div ref={containerRef} className="h-full w-full" />
        {overlay !== null && (
          <p
            role={current !== null && 'error' in current ? 'alert' : 'status'}
            className="absolute inset-0 flex items-center justify-center bg-white/80 px-6 text-center text-sm text-slate-500"
          >
            {overlay}
          </p>
        )}
      </div>
    </div>
  )
}
