import { useCallback, useEffect, useState } from 'react'

import { cancelOrder, fetchAllocation, fetchOrders, fetchPortfolio, fetchTrades } from '../api/trading'
import { OrderHistory } from '../components/OrderHistory'
import { PortfolioOverview } from '../components/PortfolioOverview'
import { TradePanel } from '../components/TradePanel'
import { useAuth } from '../context/useAuth'
import { formatUsd } from '../lib/money'
import type { AllocationSlice, Order, Portfolio, Trade } from '../types/trading'

export function Dashboard() {
  const { user, logout, refreshUser } = useAuth()
  const [orders, setOrders] = useState<Order[]>([])
  const [trades, setTrades] = useState<Trade[]>([])
  const [portfolio, setPortfolio] = useState<Portfolio | null>(null)
  const [allocation, setAllocation] = useState<AllocationSlice[]>([])

  // Each section loads on its own: one failing endpoint must not blank the others.
  const load = useCallback(async () => {
    const [nextOrders, nextTrades, nextPortfolio, nextAllocation] = await Promise.allSettled([
      fetchOrders(),
      fetchTrades(),
      fetchPortfolio(),
      fetchAllocation(),
    ])
    return { nextOrders, nextTrades, nextPortfolio, nextAllocation }
  }, [])

  const apply = useCallback((result: Awaited<ReturnType<typeof load>>) => {
    if (result.nextOrders.status === 'fulfilled') setOrders(result.nextOrders.value)
    if (result.nextTrades.status === 'fulfilled') setTrades(result.nextTrades.value)
    if (result.nextPortfolio.status === 'fulfilled') setPortfolio(result.nextPortfolio.value)
    if (result.nextAllocation.status === 'fulfilled') setAllocation(result.nextAllocation.value)
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

  async function handleCancel(id: number) {
    // A 422 here means the order filled a moment before, which the reload below then shows.
    await cancelOrder(id).catch(() => undefined)
    await reload().catch(() => undefined)
  }

  if (user === null) {
    return null
  }

  return (
    <div className="min-h-dvh bg-slate-100">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-4xl items-center justify-between px-6 py-4">
          <span className="font-semibold text-slate-900">PortfolioPro</span>
          <div className="flex items-center gap-4">
            <span className="text-sm text-slate-500">{user.email}</span>
            <button
              type="button"
              onClick={logout}
              className="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 transition hover:bg-slate-50"
            >
              Log out
            </button>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-4xl px-6 py-10">
        <h1 className="text-2xl font-semibold text-slate-900">Dashboard</h1>

        <div className="mt-6 space-y-6">
          {portfolio === null ? (
            <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
              <p className="text-sm font-medium text-slate-500">Available cash</p>
              <p className="mt-1 text-3xl font-semibold tracking-tight text-slate-900">
                {formatUsd(user.cashBalance)}
              </p>
            </div>
          ) : (
            <PortfolioOverview portfolio={portfolio} allocation={allocation} />
          )}
          <TradePanel onOrderPlaced={() => void reload().catch(() => undefined)} />
          <OrderHistory orders={orders} trades={trades} onCancel={(id) => void handleCancel(id)} />
        </div>
      </main>
    </div>
  )
}
