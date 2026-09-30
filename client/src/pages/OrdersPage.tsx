import { OrdersTable } from '../components/portfolio/OrdersTable'
import { Tabs } from '../components/ui/Tabs'
import { useTradingData } from '../data/useTradingData'

export function OrdersPage() {
  const { orders } = useTradingData()
  const all = orders.data ?? []
  return (
    <div className="h-full p-3">
      <h1 className="sr-only">Orders</h1>
      <div className="panel h-full border border-rule">
        <Tabs
          label="Orders"
          className="h-full"
          items={[
            { id: 'open', label: 'Open orders', count: all.filter((order) => order.status === 'PENDING').length, content: <OrdersTable mode="open" /> },
            { id: 'history', label: 'Order history', count: all.filter((order) => order.status !== 'PENDING').length, content: <OrdersTable mode="history" /> },
          ]}
        />
      </div>
    </div>
  )
}
