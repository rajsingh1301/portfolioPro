import { createContext } from 'react'

import type { Resource } from './useResource'
import type { AllocationSlice, Order, Portfolio, Today, Trade, WatchlistItem } from '../types/trading'

export type ResourceName = 'portfolio' | 'allocation' | 'orders' | 'trades' | 'watchlist' | 'today'

export interface TradingData {
  portfolio: Resource<Portfolio>
  allocation: Resource<AllocationSlice[]>
  orders: Resource<Order[]>
  trades: Resource<Trade[]>
  watchlist: Resource<WatchlistItem[]>
  today: Resource<Today>
  /** Bumped after every full reload, so views with their own data (charts) can re-read too. */
  version: number
  /** Re-reads everything; call it after an order lands. */
  reloadAll: () => Promise<void>
  retry: (name: ResourceName) => void
  /** Adds or removes a symbol. Resolves to an error message, or null on success. */
  toggleWatch: (symbol: string, watched: boolean) => Promise<string | null>
  cancel: (id: number) => Promise<void>
}

/** Lives apart from the provider so the provider file only exports a component. */
export const TradingDataContext = createContext<TradingData | null>(null)
