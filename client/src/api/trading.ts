import { api } from './client'
import type {
  AllocationSlice,
  Candle,
  CandleRange,
  Order,
  PlaceOrderRequest,
  Portfolio,
  Quote,
  StockSearchResult,
  Trade,
} from '../types/trading'

export async function searchStocks(query: string): Promise<StockSearchResult[]> {
  const { data } = await api.get<StockSearchResult[]>('/stocks/search', { params: { q: query } })
  return data
}

export async function fetchQuote(symbol: string): Promise<Quote> {
  const { data } = await api.get<Quote>(`/stocks/${encodeURIComponent(symbol)}/quote`)
  return data
}

export async function placeOrder(request: PlaceOrderRequest): Promise<Order> {
  const { data } = await api.post<Order>('/orders', request)
  return data
}

export async function cancelOrder(id: number): Promise<Order> {
  const { data } = await api.delete<Order>(`/orders/${id}`)
  return data
}

export async function fetchOrders(): Promise<Order[]> {
  const { data } = await api.get<Order[]>('/orders')
  return data
}

export async function fetchTrades(): Promise<Trade[]> {
  const { data } = await api.get<Trade[]>('/trades')
  return data
}

export async function fetchPortfolio(): Promise<Portfolio> {
  const { data } = await api.get<Portfolio>('/portfolio')
  return data
}

export async function fetchAllocation(): Promise<AllocationSlice[]> {
  const { data } = await api.get<AllocationSlice[]>('/portfolio/allocation')
  return data
}

export async function fetchCandles(symbol: string, range: CandleRange): Promise<Candle[]> {
  const { data } = await api.get<Candle[]>(`/stocks/${encodeURIComponent(symbol)}/candles`, { params: { range } })
  return data
}
