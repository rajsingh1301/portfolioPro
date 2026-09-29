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

/** Mirrors com.portfoliopro.trading.dto.PlaceOrderRequest. */
export interface PlaceOrderRequest {
  symbol: string
  side: OrderSide
  type: 'MARKET'
  quantity: number
}

/** Mirrors com.portfoliopro.trading.dto.OrderResponse. */
export interface Order {
  id: number
  symbol: string
  side: OrderSide
  type: string
  quantity: number
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
