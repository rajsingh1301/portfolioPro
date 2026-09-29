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
