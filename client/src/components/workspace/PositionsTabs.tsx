import { useTradingData } from '../../data/useTradingData'
import { HoldingsTable } from '../portfolio/HoldingsTable'
import { OrdersTable } from '../portfolio/OrdersTable'
import { Tabs } from '../ui/Tabs'

/** Holdings, open orders and order history: the three things to check right after a trade. */
export function PositionsTabs({ className = '' }: { className?: string }) {
  const { portfolio, orders } = useTradingData()
  const all = orders.data ?? []
  return (
    <Tabs
      label="Positions and orders"
      className={className}
      items={[
        { id: 'holdings', label: 'Holdings', count: portfolio.data?.holdings.length, content: <HoldingsTable /> },
        { id: 'open', label: 'Open orders', count: all.filter((order) => order.status === 'PENDING').length, content: <OrdersTable mode="open" /> },
        { id: 'history', label: 'Order history', count: all.filter((order) => order.status !== 'PENDING').length, content: <OrdersTable mode="history" /> },
      ]}
    />
  )
}
