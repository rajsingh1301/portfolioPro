import { api } from './client'
import type { Order, PlaceOrderRequest, Quote, StockSearchResult, Trade } from '../types/trading'

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

export async function fetchOrders(): Promise<Order[]> {
  const { data } = await api.get<Order[]>('/orders')
  return data
}

export async function fetchTrades(): Promise<Trade[]> {
  const { data } = await api.get<Trade[]>('/trades')
  return data
}
