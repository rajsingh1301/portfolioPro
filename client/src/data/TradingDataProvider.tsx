import { useCallback, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'

import { errorMessage } from '../api/client'
import {
  addToWatchlist,
  cancelOrder,
  fetchAllocation,
  fetchOrders,
  fetchPortfolio,
  fetchToday,
  fetchTrades,
  fetchWatchlist,
  removeFromWatchlist,
} from '../api/trading'
import { TradingDataContext } from './tradingDataContext'
import type { ResourceName, TradingData } from './tradingDataContext'
import { useResource } from './useResource'

/** Server quotes are cached for 15s, so asking more often would only repeat the last answer. */
const POLL_MS = 15_000

/**
 * Everything the signed-in pages share, loaded once and kept fresh: moving between the
 * dashboard, the portfolio and the orders page does not refetch, and a price that changes on
 * one page has changed on all of them.
 */
export function TradingDataProvider({ children }: { children: ReactNode }) {
  const portfolio = useResource(fetchPortfolio, 'Could not load your portfolio')
  const allocation = useResource(fetchAllocation, 'Could not load your allocation')
  const orders = useResource(fetchOrders, 'Could not load your orders')
  const trades = useResource(fetchTrades, 'Could not load your trades')
  const watchlist = useResource(fetchWatchlist, 'Could not load your watchlist')
  const today = useResource(fetchToday, "Could not load today's result")
  const [version, setVersion] = useState(0)

  const { reload: reloadPortfolio, retry: retryPortfolio } = portfolio
  const { reload: reloadAllocation, retry: retryAllocation } = allocation
  const { reload: reloadOrders, retry: retryOrders } = orders
  const { reload: reloadTrades, retry: retryTrades } = trades
  const { reload: reloadWatchlist, retry: retryWatchlist } = watchlist
  const { reload: reloadToday, retry: retryToday } = today

  const reloadAll = useCallback(async () => {
    await Promise.all([
      reloadPortfolio(),
      reloadAllocation(),
      reloadOrders(),
      reloadTrades(),
      reloadWatchlist(),
      reloadToday(),
    ])
    setVersion((current) => current + 1)
  }, [reloadPortfolio, reloadAllocation, reloadOrders, reloadTrades, reloadWatchlist, reloadToday])

  // Live prices: re-read every 15s while the tab is showing, and once more as soon as it is shown
  // again. A hidden tab polls nothing, which also spares the shared Finnhub quota.
  useEffect(() => {
    const tick = () => {
      if (document.visibilityState === 'visible') {
        void reloadAll()
      }
    }
    const timer = window.setInterval(tick, POLL_MS)
    document.addEventListener('visibilitychange', tick)
    return () => {
      window.clearInterval(timer)
      document.removeEventListener('visibilitychange', tick)
    }
  }, [reloadAll])

  const retry = useCallback(
    (name: ResourceName) => {
      const retries: Record<ResourceName, () => void> = {
        portfolio: retryPortfolio,
        allocation: retryAllocation,
        orders: retryOrders,
        trades: retryTrades,
        watchlist: retryWatchlist,
        today: retryToday,
      }
      retries[name]()
    },
    [retryPortfolio, retryAllocation, retryOrders, retryTrades, retryWatchlist, retryToday],
  )

  const toggleWatch = useCallback(
    async (symbol: string, watched: boolean) => {
      try {
        if (watched) {
          await removeFromWatchlist(symbol)
        } else {
          await addToWatchlist(symbol)
        }
        await reloadWatchlist()
        return null
      } catch (failure) {
        return errorMessage(failure)
      }
    },
    [reloadWatchlist],
  )

  const cancel = useCallback(
    async (id: number) => {
      // A 422 here means the order filled a moment before, which the reload below then shows.
      await cancelOrder(id).catch(() => undefined)
      await reloadAll()
    },
    [reloadAll],
  )

  const value = useMemo<TradingData>(
    () => ({
      portfolio: portfolio.state,
      allocation: allocation.state,
      orders: orders.state,
      trades: trades.state,
      watchlist: watchlist.state,
      today: today.state,
      version,
      reloadAll,
      retry,
      toggleWatch,
      cancel,
    }),
    [portfolio.state, allocation.state, orders.state, trades.state, watchlist.state, today.state, version, reloadAll, retry, toggleWatch, cancel],
  )

  return <TradingDataContext.Provider value={value}>{children}</TradingDataContext.Provider>
}
