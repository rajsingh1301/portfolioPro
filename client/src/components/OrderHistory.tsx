import { formatUsd } from '../lib/money'
import type { Order, Trade } from '../types/trading'

interface OrderHistoryProps {
  orders: Order[]
  trades: Trade[]
  onCancel: (id: number) => void
  className?: string
}

const TYPE_LABEL = { MARKET: 'Market', LIMIT: 'Limit', STOP_LOSS: 'Stop-loss' } as const

export function OrderHistory({ orders, trades, onCancel, className = '' }: OrderHistoryProps) {
  // A fill's price lives on the trade, so orders are joined to it here.
  const priceByOrder = new Map(trades.map((trade) => [trade.orderId, trade.price]))

  return (
    <section aria-labelledby="orders-heading" className={`section ${className}`}>
      <h2 id="orders-heading" className="section-title">
        Orders
      </h2>
      {orders.length === 0 ? (
        <p className="mt-4 max-w-md text-sm text-ink-2">
          No orders yet. Every order you place is listed here, including the ones a risk limit turned away.
        </p>
      ) : (
        <div className="mt-3 overflow-x-auto">
          <table className="data-table">
            <thead>
              <tr>
                <th scope="col" className="hidden md:table-cell">Time</th>
                <th scope="col">Symbol</th>
                <th scope="col" className="hidden sm:table-cell">Side</th>
                <th scope="col" className="hidden sm:table-cell">Type</th>
                <th scope="col" className="num">Shares</th>
                <th scope="col" className="num">Price</th>
                <th scope="col">Status</th>
                <th scope="col"><span className="sr-only">Actions</span></th>
              </tr>
            </thead>
            <tbody>
              {orders.map((order) => {
                const fillPrice = priceByOrder.get(order.id)
                const target = order.limitPrice ?? order.triggerPrice
                const rejected = order.status === 'REJECTED'
                return (
                  <tr key={order.id}>
                    <td className="hidden whitespace-nowrap text-ink-3 md:table-cell">
                      {new Date(order.createdAt).toLocaleString()}
                    </td>
                    <td className="font-medium">
                      {order.symbol}
                      <span className="block text-xs font-normal text-ink-3 sm:hidden">
                        {order.side === 'BUY' ? 'Buy' : 'Sell'} · {TYPE_LABEL[order.type]}
                      </span>
                    </td>
                    <td className="hidden sm:table-cell">{order.side === 'BUY' ? 'Buy' : 'Sell'}</td>
                    <td className="hidden sm:table-cell">{TYPE_LABEL[order.type]}</td>
                    <td className="num">{order.quantity}</td>
                    <td className="num">
                      {fillPrice !== undefined
                        ? formatUsd(fillPrice)
                        : target !== undefined
                          ? `${order.type === 'STOP_LOSS' ? 'stop ' : 'limit '}${formatUsd(target)}`
                          : '—'}
                    </td>
                    <td className={rejected ? 'text-loss' : order.status === 'PENDING' ? 'font-medium' : 'text-ink-2'}>
                      {rejected ? `Rejected: ${order.rejectReason ?? ''}` : order.status.toLowerCase()}
                    </td>
                    <td className="text-right">
                      {order.status === 'PENDING' && (
                        <button type="button" onClick={() => onCancel(order.id)} className="btn btn-sm">
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
