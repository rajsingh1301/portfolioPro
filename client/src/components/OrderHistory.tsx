import { formatUsd } from '../lib/money'
import type { Order, Trade } from '../types/trading'

interface OrderHistoryProps {
  orders: Order[]
  trades: Trade[]
}

export function OrderHistory({ orders, trades }: OrderHistoryProps) {
  // A fill's price lives on the trade, so orders are joined to it here.
  const priceByOrder = new Map(trades.map((trade) => [trade.orderId, trade.price]))

  return (
    <section className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
      <h2 className="text-lg font-semibold text-slate-900">Orders</h2>
      {orders.length === 0 ? (
        <p className="mt-3 text-sm text-slate-500">No orders yet.</p>
      ) : (
        <div className="mt-3 overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="text-xs uppercase text-slate-500">
              <tr>
                <th className="py-2 pr-4 font-medium">Time</th>
                <th className="py-2 pr-4 font-medium">Symbol</th>
                <th className="py-2 pr-4 font-medium">Side</th>
                <th className="py-2 pr-4 text-right font-medium">Shares</th>
                <th className="py-2 pr-4 text-right font-medium">Price</th>
                <th className="py-2 font-medium">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {orders.map((order) => {
                const price = priceByOrder.get(order.id)
                return (
                  <tr key={order.id}>
                    <td className="py-2 pr-4 text-slate-500">{new Date(order.createdAt).toLocaleString()}</td>
                    <td className="py-2 pr-4 font-medium text-slate-900">{order.symbol}</td>
                    <td className="py-2 pr-4">{order.side === 'BUY' ? 'Buy' : 'Sell'}</td>
                    <td className="py-2 pr-4 text-right">{order.quantity}</td>
                    <td className="py-2 pr-4 text-right">{price === undefined ? '—' : formatUsd(price)}</td>
                    <td className={`py-2 ${order.status === 'REJECTED' ? 'text-red-600' : 'text-slate-700'}`}>
                      {order.status === 'REJECTED' ? `Rejected: ${order.rejectReason ?? ''}` : order.status.toLowerCase()}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
