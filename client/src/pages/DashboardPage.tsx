import { Group, Panel, useDefaultLayout, usePanelRef } from 'react-resizable-panels'
import { useSearchParams } from 'react-router-dom'

import { ChartPanel } from '../components/workspace/ChartPanel'
import { OrderTicket } from '../components/workspace/OrderTicket'
import { PositionsTabs } from '../components/workspace/PositionsTabs'
import { ResizeHandle } from '../components/workspace/ResizeHandle'
import { useCollapsible } from '../components/workspace/useCollapsible'
import { WatchlistTable } from '../components/workspace/WatchlistTable'
import { Icon } from '../components/ui/Icon'
import { Tabs } from '../components/ui/Tabs'
import { layoutStorage } from '../lib/layoutStorage'
import { useIsDesktop } from '../lib/useMediaQuery'
import { useQuote } from '../lib/useQuote'
import { useSelectedSymbol } from '../lib/useSelectedSymbol'

/**
 * The trading workspace: the chart in the middle, the watchlist and the order ticket down the
 * right, holdings and orders along the bottom. On a wide screen the panels resize and collapse and
 * remember their sizes; on a phone they become tabs.
 */
export function DashboardPage() {
  const { symbol } = useSelectedSymbol()
  const quote = useQuote(symbol)
  const desktop = useIsDesktop()
  return (
    <div className="h-full">
      <h1 className="sr-only">Dashboard</h1>
      {desktop ? <Workspace symbol={symbol} quote={quote} /> : <MobileWorkspace symbol={symbol} quote={quote} />}
    </div>
  )
}

type Props = { symbol: string; quote: ReturnType<typeof useQuote> }

function Workspace({ symbol, quote }: Props) {
  const outer = useDefaultLayout({ id: 'pp-workspace-rows', storage: layoutStorage, panelIds: ['top', 'bottom'] })
  const columns = useDefaultLayout({ id: 'pp-workspace-columns', storage: layoutStorage, panelIds: ['chart', 'rail'] })
  const rail = useDefaultLayout({ id: 'pp-rail-rows', storage: layoutStorage, panelIds: ['watchlist', 'ticket'] })
  const railRef = usePanelRef()
  const bottomRef = usePanelRef()
  const railState = useCollapsible(railRef)
  const bottomState = useCollapsible(bottomRef)

  const toggles = (
    <div className="flex gap-1">
      <button
        type="button"
        onClick={railState.toggle}
        aria-pressed={!railState.collapsed}
        aria-label={railState.collapsed ? 'Show the watchlist and order panel' : 'Hide the watchlist and order panel'}
        title={railState.collapsed ? 'Show the side panel' : 'Hide the side panel'}
        className="btn btn-quiet size-7 px-0"
      >
        <Icon name="panelRight" size={16} />
      </button>
      <button
        type="button"
        onClick={bottomState.toggle}
        aria-pressed={!bottomState.collapsed}
        aria-label={bottomState.collapsed ? 'Show holdings and orders' : 'Hide holdings and orders'}
        title={bottomState.collapsed ? 'Show holdings and orders' : 'Hide holdings and orders'}
        className="btn btn-quiet size-7 px-0"
      >
        <Icon name="panelBottom" size={16} />
      </button>
    </div>
  )

  return (
    <Group
      orientation="vertical"
      defaultLayout={outer.defaultLayout}
      onLayoutChanged={outer.onLayoutChanged}
      className="h-full"
    >
      <Panel id="top" minSize="30%">
        <Group
          orientation="horizontal"
          defaultLayout={columns.defaultLayout}
          onLayoutChanged={columns.onLayoutChanged}
          className="h-full"
        >
          <Panel id="chart" minSize="35%">
            <ChartPanel symbol={symbol} quote={quote} actions={toggles} />
          </Panel>
          <ResizeHandle orientation="vertical" />
          <Panel
            id="rail"
            panelRef={railRef}
            defaultSize="27%"
            minSize={280}
            collapsible
            collapsedSize={0}
            onResize={railState.onResize}
          >
            <Group
              orientation="vertical"
              defaultLayout={rail.defaultLayout}
              onLayoutChanged={rail.onLayoutChanged}
              className="h-full"
            >
              {/* The ticket needs about 320px to show everything without scrolling; the watchlist scrolls anyway. */}
              <Panel id="watchlist" defaultSize="34%" minSize={120}>
                <WatchlistTable activeSymbol={symbol} />
              </Panel>
              <ResizeHandle orientation="horizontal" />
              <Panel id="ticket" minSize={320}>
                <OrderTicket symbol={symbol} quote={quote} />
              </Panel>
            </Group>
          </Panel>
        </Group>
      </Panel>
      <ResizeHandle orientation="horizontal" />
      <Panel
        id="bottom"
        panelRef={bottomRef}
        defaultSize="32%"
        minSize={140}
        collapsible
        collapsedSize={32}
        onResize={bottomState.onResize}
      >
        <div className="panel h-full">
          <PositionsTabs className="h-full" />
        </div>
      </Panel>
    </Group>
  )
}

/** One panel at a time. Pressing B or S jumps to the order ticket. */
function MobileWorkspace({ symbol, quote }: Props) {
  const [params] = useSearchParams()
  const wantsTicket = params.get('side') !== null
  return (
    <Tabs
      label="Workspace"
      className="h-full"
      requested={wantsTicket ? 'order' : null}
      items={[
        {
          id: 'chart',
          label: 'Chart',
          content: (
            <div className="h-[calc(100dvh-8.5rem)] min-h-96">
              <ChartPanel symbol={symbol} quote={quote} />
            </div>
          ),
        },
        { id: 'watchlist', label: 'Watchlist', content: <WatchlistTable activeSymbol={symbol} /> },
        { id: 'order', label: 'Order', content: <OrderTicket symbol={symbol} quote={quote} /> },
        { id: 'positions', label: 'Positions', content: <PositionsTabs className="h-full" /> },
      ]}
    />
  )
}
