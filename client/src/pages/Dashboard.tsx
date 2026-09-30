import { useCallback, useEffect, useState } from 'react'

import {
  addToWatchlist,
  cancelOrder,
  fetchAllocation,
  fetchOrders,
  fetchPortfolio,
  fetchTrades,
  fetchWatchlist,
  removeFromWatchlist,
} from '../api/trading'
import { errorMessage } from '../api/client'
import { OrderHistory } from '../components/OrderHistory'
import { AllocationSection, HoldingsSection, PortfolioMasthead } from '../components/PortfolioOverview'
import { RiskSettingsCard } from '../components/RiskSettingsCard'
import { TradePanel } from '../components/TradePanel'
import { WatchlistCard } from '../components/WatchlistCard'
import { useAuth } from '../context/useAuth'
import type { AllocationSlice, Order, Portfolio, Trade, WatchlistItem } from '../types/trading'

export function Dashboard() {
  const { user, logout, refreshUser } = useAuth()
  const [orders, setOrders] = useState<Order[]>([])
  const [trades, setTrades] = useState<Trade[]>([])
  const [portfolio, setPortfolio] = useState<Portfolio | null>(null)
  const [allocation, setAllocation] = useState<AllocationSlice[]>([])
  const [watchlist, setWatchlist] = useState<WatchlistItem[]>([])
  const [watchError, setWatchError] = useState<string | null>(null)
  const [requested, setRequested] = useState<{ symbol: string } | null>(null)

  // Each section loads on its own: one failing endpoint must not blank the others.
  const load = useCallback(async () => {
    const [nextOrders, nextTrades, nextPortfolio, nextAllocation, nextWatchlist] = await Promise.allSettled([
      fetchOrders(),
      fetchTrades(),
      fetchPortfolio(),
      fetchAllocation(),
      fetchWatchlist(),
    ])
    return { nextOrders, nextTrades, nextPortfolio, nextAllocation, nextWatchlist }
  }, [])

  const apply = useCallback((result: Awaited<ReturnType<typeof load>>) => {
    if (result.nextOrders.status === 'fulfilled') setOrders(result.nextOrders.value)
    if (result.nextTrades.status === 'fulfilled') setTrades(result.nextTrades.value)
    if (result.nextPortfolio.status === 'fulfilled') setPortfolio(result.nextPortfolio.value)
    if (result.nextAllocation.status === 'fulfilled') setAllocation(result.nextAllocation.value)
    if (result.nextWatchlist.status === 'fulfilled') setWatchlist(result.nextWatchlist.value)
  }, [])

  const reload = useCallback(async () => {
    const [result] = await Promise.all([load(), refreshUser().catch(() => undefined)])
    apply(result)
  }, [load, apply, refreshUser])

  useEffect(() => {
    let active = true
    load().then((result) => {
      if (active) {
        apply(result)
      }
    })
    return () => {
      active = false
    }
  }, [load, apply])

  const hasPending = orders.some((order) => order.status === 'PENDING')

  // The scheduler fills pending orders on its own, so while any are waiting the page
  // re-reads now and then rather than showing a stale "pending" for an order that filled.
  useEffect(() => {
    if (!hasPending) {
      return
    }
    const timer = window.setInterval(() => void reload().catch(() => undefined), 15000)
    return () => window.clearInterval(timer)
  }, [hasPending, reload])

  // Watchlist prices drift on their own, so they are re-read now and then while any are followed.
  const followsAny = watchlist.length > 0
  useEffect(() => {
    if (!followsAny) {
      return
    }
    const timer = window.setInterval(() => {
      fetchWatchlist()
        .then(setWatchlist)
        .catch(() => undefined)
    }, 30000)
    return () => window.clearInterval(timer)
  }, [followsAny])

  async function toggleWatch(symbol: string, watched: boolean) {
    setWatchError(null)
    try {
      if (watched) {
        await removeFromWatchlist(symbol)
      } else {
        await addToWatchlist(symbol)
      }
      setWatchlist(await fetchWatchlist())
    } catch (failure) {
      setWatchError(errorMessage(failure))
    }
  }

  async function handleCancel(id: number) {
    // A 422 here means the order filled a moment before, which the reload below then shows.
    await cancelOrder(id).catch(() => undefined)
    await reload().catch(() => undefined)
  }

  if (user === null) {
    return null
  }

  return (
    <div className="min-h-dvh">
      <a href="#main" className="skip-link">
        Skip to content
      </a>
      <header className="border-b border-rule">
        <div className="mx-auto flex h-14 max-w-[80rem] items-center justify-between px-4 sm:px-6 lg:px-10">
          <span className="font-display text-xl font-semibold tracking-tight">PortfolioPro</span>
          <div className="flex items-center gap-3 sm:gap-5">
            <span className="hidden text-sm text-ink-3 sm:inline">{user.email}</span>
            <button type="button" onClick={logout} className="btn btn-sm">
              Log out
            </button>
          </div>
        </div>
      </header>

      <main id="main" className="mx-auto max-w-[80rem] px-4 pb-24 pt-8 sm:px-6 lg:px-10 lg:pt-12">
        <h1 className="sr-only">Dashboard</h1>
        <PortfolioMasthead portfolio={portfolio} cash={user.cashBalance} />

        {/*
          One column on a phone, in the order a person reaches for things; two on a wide screen,
          the workspace wide and a narrow rail beside it. The wrappers vanish (display: contents)
          below `lg`, so `order` interleaves all six sections in a single flow there.
        */}
        <div className="mt-12 flex flex-col gap-12 lg:grid lg:grid-cols-[minmax(0,1fr)_22rem] lg:items-start lg:gap-x-14">
          <div className="contents lg:flex lg:flex-col lg:gap-12">
            <TradePanel
              className="order-1"
              onOrderPlaced={() => void reload().catch(() => undefined)}
              watchedSymbols={watchlist.map((item) => item.symbol)}
              onToggleWatch={(symbol, watched) => void toggleWatch(symbol, watched)}
              requested={requested}
            />
            <HoldingsSection portfolio={portfolio} className="order-3" />
            <OrderHistory
              className="order-4"
              orders={orders}
              trades={trades}
              onCancel={(id) => void handleCancel(id)}
            />
          </div>
          <div className="contents lg:flex lg:flex-col lg:gap-12">
            <WatchlistCard
              className="order-2"
              items={watchlist}
              error={watchError}
              onSelect={(symbol) => setRequested({ symbol })}
              onRemove={(symbol) => void toggleWatch(symbol, true)}
            />
            <AllocationSection allocation={allocation} className="order-5" />
            <RiskSettingsCard className="order-6" />
          </div>
        </div>
      </main>
    </div>
  )
}
