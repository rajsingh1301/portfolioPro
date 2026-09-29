import { useCallback, useEffect, useState } from 'react'

import { fetchOrders, fetchTrades } from '../api/trading'
import { OrderHistory } from '../components/OrderHistory'
import { TradePanel } from '../components/TradePanel'
import { useAuth } from '../context/useAuth'
import { formatUsd } from '../lib/money'
import type { Order, Trade } from '../types/trading'

export function Dashboard() {
  const { user, logout, refreshUser } = useAuth()
  const [orders, setOrders] = useState<Order[]>([])
  const [trades, setTrades] = useState<Trade[]>([])

  const reload = useCallback(async () => {
    const [nextOrders, nextTrades] = await Promise.all([fetchOrders(), fetchTrades(), refreshUser()])
    setOrders(nextOrders)
    setTrades(nextTrades)
  }, [refreshUser])

  useEffect(() => {
    let active = true
    Promise.all([fetchOrders(), fetchTrades()])
      .then(([nextOrders, nextTrades]) => {
        if (active) {
          setOrders(nextOrders)
          setTrades(nextTrades)
        }
      })
      .catch(() => {
        // Lists stay empty; a 401 is already handled by the interceptor.
      })
    return () => {
      active = false
    }
  }, [])

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

        <div className="mt-6 rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <p className="text-sm font-medium text-slate-500">Available cash</p>
          <p className="mt-1 text-3xl font-semibold tracking-tight text-slate-900">
            {formatUsd(user.cashBalance)}
          </p>
        </div>

        <div className="mt-6 space-y-6">
          <TradePanel onOrderPlaced={() => void reload().catch(() => undefined)} />
          <OrderHistory orders={orders} trades={trades} />
        </div>
      </main>
    </div>
  )
}
