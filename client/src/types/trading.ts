/** Mirrors com.portfoliopro.market.dto.StockSearchResult. */
export interface StockSearchResult {
  symbol: string
  name: string
}

/** Mirrors com.portfoliopro.market.dto.QuoteResponse. Prices are strings, never numbers. */
export interface Quote {
  symbol: string
  price: string
  change: string | null
  percentChange: string | null
  asOf: string
}

export type OrderSide = 'BUY' | 'SELL'
export type OrderType = 'MARKET' | 'LIMIT' | 'STOP_LOSS'

/** Mirrors com.portfoliopro.trading.dto.PlaceOrderRequest. Prices are strings, never numbers. */
export interface PlaceOrderRequest {
  symbol: string
  side: OrderSide
  type: OrderType
  quantity: number
  /** Required for LIMIT. */
  limitPrice?: string
  /** Required for STOP_LOSS, which is always a sell. */
  triggerPrice?: string
  /** On a buy: also place a stop-loss below the fill price once it fills. */
  attachStopLoss?: boolean
}

/** Mirrors com.portfoliopro.trading.dto.OrderResponse. */
export interface Order {
  id: number
  symbol: string
  side: OrderSide
  type: OrderType
  quantity: number
  limitPrice?: string
  triggerPrice?: string
  attachStopLoss: boolean
  status: 'PENDING' | 'FILLED' | 'PARTIAL' | 'CANCELLED' | 'REJECTED'
  rejectReason: string | null
  createdAt: string
}

/** Mirrors com.portfoliopro.trading.dto.TradeResponse. */
export interface Trade {
  id: number
  orderId: number
  symbol: string
  side: OrderSide
  quantity: number
  price: string
  executedAt: string
}

/** Mirrors com.portfoliopro.portfolio.dto.HoldingResponse. Null fields are omitted by the API. */
export interface Holding {
  symbol: string
  quantity: number
  avgPrice: string
  /** Absent when the quote could not be fetched; marketValue is then the cost. */
  price?: string
  marketValue: string
  unrealizedPnl?: string
  unrealizedPnlPercent?: string
  realizedPnl: string
}

/** Mirrors com.portfoliopro.portfolio.dto.PortfolioResponse. */
export interface Portfolio {
  cash: string
  holdingsValue: string
  totalValue: string
  unrealizedPnl: string
  realizedPnl: string
  holdings: Holding[]
}

/** Mirrors com.portfoliopro.portfolio.dto.AllocationSlice. `symbol` is absent for cash. */
export interface AllocationSlice {
  symbol?: string
  label: string
  value: string
  percent: string
}

export const CANDLE_RANGES = ['1D', '1W', '1M', '6M', '1Y', '5Y'] as const
export type CandleRange = (typeof CANDLE_RANGES)[number]

/** Mirrors com.portfoliopro.market.dto.CandleResponse. `time` is UTC epoch seconds. */
export interface Candle {
  time: number
  open: string
  high: string
  low: string
  close: string
  volume: number
}

/** Mirrors com.portfoliopro.portfolio.dto.WatchlistItem. Fields the API could not fill are absent. */
export interface WatchlistItem {
  symbol: string
  name?: string
  price?: string
  change?: string
  percentChange?: string
}

/** Mirrors com.portfoliopro.analysis.dto.IndicatorPoint. */
export interface IndicatorPoint {
  time: number
  value: string
}

export interface Reading {
  indicator: string
  label: string
}

/** Mirrors com.portfoliopro.analysis.dto.IndicatorResponse. */
export interface Indicators {
  symbol: string
  range: CandleRange
  sma20: IndicatorPoint[]
  sma50: IndicatorPoint[]
  ema20: IndicatorPoint[]
  rsi14: IndicatorPoint[]
  macd: { line: IndicatorPoint[]; signal: IndicatorPoint[]; histogram: IndicatorPoint[] }
  bollinger: { upper: IndicatorPoint[]; middle: IndicatorPoint[]; lower: IndicatorPoint[] }
  readings: Reading[]
  disclaimer: string
}

/** Mirrors com.portfoliopro.analysis.dto.FundamentalsResponse. Anything unreported is absent. */
export interface Fundamentals {
  symbol: string
  name?: string
  exchange?: string
  industry?: string
  /** Whole US dollars. */
  marketCap?: string
  peRatio?: string
  eps?: string
  /** A percentage. */
  roe?: string
  /** A percentage. */
  dividendYield?: string
  week52High?: string
  week52Low?: string
  beta?: string
}

export interface LimitRange {
  min: string
  max: string
}

/** Mirrors com.portfoliopro.risk.dto.RiskSettingsResponse. The three limits are decimal strings. */
export interface RiskSettings {
  maxPositionPct: string
  maxOrderValue: string
  defaultStopLossPct: string
  bounds: { maxPositionPct: LimitRange; maxOrderValue: LimitRange; defaultStopLossPct: LimitRange }
  defaults: { maxPositionPct: string; maxOrderValue: string; defaultStopLossPct: string }
}

export type RiskLimitsInput = Pick<RiskSettings, 'maxPositionPct' | 'maxOrderValue' | 'defaultStopLossPct'>

export const PERFORMANCE_RANGES = ['1M', '3M', '6M', '1Y'] as const
export type PerformanceRange = (typeof PERFORMANCE_RANGES)[number]

/** Mirrors com.portfoliopro.trading.dto.PerformanceResponse. Dates are UTC calendar days, money is a string. */
export interface Performance {
  range: PerformanceRange
  from: string
  to: string
  points: { date: string; value: string; cash: string; invested: string }[]
  summary: {
    startValue: string
    endValue: string
    change: string
    changePercent: string
    bestDay?: DayMove
    worstDay?: DayMove
  }
  positions: PositionResult[]
  /** Symbols valued at their trade price on some day, because no price history was available. */
  estimatedSymbols: string[]
}

export interface DayMove {
  date: string
  change: string
  changePercent: string
}

export interface PositionResult {
  symbol: string
  open: boolean
  quantity: number
  realizedPnl: string
  unrealizedPnl?: string
  totalPnl: string
}
