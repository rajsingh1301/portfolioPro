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
