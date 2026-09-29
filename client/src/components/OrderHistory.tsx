import { formatUsd } from '../lib/money'
import type { Order, Trade } from '../types/trading'

interface OrderHistoryProps {
  orders: Order[]
  trades: Trade[]
  onCancel: (id: number) => void
}

const TYPE_LABEL = { MARKET: 'Market', LIMIT: 'Limit', STOP_LOSS: 'Stop-loss' } as const

export function OrderHistory({ orders, trades, onCancel }: OrderHistoryProps) {
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
                <th className="py-2 pr-4 font-medium">Type</th>
                <th className="py-2 pr-4 text-right font-medium">Shares</th>
                <th className="py-2 pr-4 text-right font-medium">Price</th>
                <th className="py-2 pr-4 font-medium">Status</th>
                <th className="py-2 font-medium"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {orders.map((order) => {
                const fillPrice = priceByOrder.get(order.id)
                const target = order.limitPrice ?? order.triggerPrice
                return (
                  <tr key={order.id}>
                    <td className="py-2 pr-4 text-slate-500">{new Date(order.createdAt).toLocaleString()}</td>
                    <td className="py-2 pr-4 font-medium text-slate-900">{order.symbol}</td>
                    <td className="py-2 pr-4">{order.side === 'BUY' ? 'Buy' : 'Sell'}</td>
                    <td className="py-2 pr-4">{TYPE_LABEL[order.type]}</td>
                    <td className="py-2 pr-4 text-right">{order.quantity}</td>
                    <td className="py-2 pr-4 text-right">
                      {fillPrice !== undefined
                        ? formatUsd(fillPrice)
                        : target !== undefined
                          ? `${order.type === 'STOP_LOSS' ? 'stop ' : 'limit '}${formatUsd(target)}`
                          : '—'}
                    </td>
                    <td className={`py-2 pr-4 ${order.status === 'REJECTED' ? 'text-red-600' : 'text-slate-700'}`}>
                      {order.status === 'REJECTED' ? `Rejected: ${order.rejectReason ?? ''}` : order.status.toLowerCase()}
                    </td>
                    <td className="py-2 text-right">
                      {order.status === 'PENDING' && (
                        <button
                          type="button"
                          onClick={() => onCancel(order.id)}
                          className="rounded-md border border-slate-300 px-2 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50"
                        >
                          Cancel
                        </button>
                      )}
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
