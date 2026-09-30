import { useTradingData } from '../../data/useTradingData'
import { formatUsd } from '../../lib/money'
import { EmptyState, ErrorState, SkeletonRows } from '../ui/states'

const TYPE_LABEL = { MARKET: 'Market', LIMIT: 'Limit', STOP_LOSS: 'Stop-loss' } as const

/**
 * Orders. `open` is what is still waiting (limit and stop-loss orders), with a Cancel button;
 * `history` is everything settled: filled, rejected (with the reason) and cancelled.
 */
export function OrdersTable({ mode }: { mode: 'open' | 'history' }) {
  const { orders, trades, retry, cancel } = useTradingData()

  if (orders.loading) {
    return <SkeletonRows rows={4} columns={6} />
  }
  if (orders.error !== null && orders.data === null) {
    return <ErrorState message={orders.error} onRetry={() => retry('orders')} />
  }

  // A fill's price lives on the trade, so orders are joined to it here.
  const priceByOrder = new Map((trades.data ?? []).map((trade) => [trade.orderId, trade.price]))
  const rows = (orders.data ?? []).filter((order) => (mode === 'open' ? order.status === 'PENDING' : order.status !== 'PENDING'))

  if (rows.length === 0) {
    return (
      <EmptyState>
        {mode === 'open'
          ? 'No open orders. A limit or stop-loss order waits here until its price is reached.'
          : 'No order history yet. Every order you place is listed here, including the ones a risk limit turned away.'}
      </EmptyState>
    )
  }

  return (
    <table className="grid-table">
      <caption className="sr-only">{mode === 'open' ? 'Open orders' : 'Order history'}</caption>
      <thead>
        <tr>
          <th scope="col">Time</th>
          <th scope="col">Symbol</th>
          <th scope="col">Side</th>
          <th scope="col">Type</th>
          <th scope="col" className="num">Qty</th>
          <th scope="col" className="num">Price</th>
          <th scope="col">Status</th>
          {mode === 'open' && (
            <th scope="col">
              <span className="sr-only">Actions</span>
            </th>
          )}
        </tr>
      </thead>
      <tbody>
        {rows.map((order) => {
          const fillPrice = priceByOrder.get(order.id)
          const target = order.limitPrice ?? order.triggerPrice
          const rejected = order.status === 'REJECTED'
          return (
            <tr key={order.id}>
              <td className="text-ink-3">{new Date(order.createdAt).toLocaleString()}</td>
              <td className="font-semibold">{order.symbol}</td>
              <td>{order.side === 'BUY' ? 'Buy' : 'Sell'}</td>
              <td>{TYPE_LABEL[order.type]}</td>
              <td className="num">{order.quantity}</td>
              <td className="num">
                {fillPrice !== undefined
                  ? formatUsd(fillPrice)
                  : target !== undefined
                    ? `${order.type === 'STOP_LOSS' ? 'stop ' : 'limit '}${formatUsd(target)}`
                    : '—'}
              </td>
              <td className={rejected ? 'text-down' : order.status === 'PENDING' ? 'font-medium' : 'text-ink-2'}>
                {rejected ? `Rejected: ${order.rejectReason ?? ''}` : order.status.toLowerCase()}
              </td>
              {mode === 'open' && (
                <td className="text-right">
                  <button type="button" onClick={() => void cancel(order.id)} className="btn btn-sm">
                    Cancel
                  </button>
                </td>
              )}
            </tr>
          )
        })}
      </tbody>
    </table>
  )
}
