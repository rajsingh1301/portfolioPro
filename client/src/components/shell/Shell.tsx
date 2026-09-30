import { useCallback, useMemo, useState } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'

import { ShellContext } from '../../context/shellContext'
import type { ShellContextValue } from '../../context/shellContext'
import { TradingDataProvider } from '../../data/TradingDataProvider'
import { rememberSymbol, rememberedSymbol } from '../../lib/useSelectedSymbol'
import { BottomNav } from './BottomNav'
import { CommandPalette } from './CommandPalette'
import { Sidebar } from './Sidebar'
import { TopBar } from './TopBar'
import { useGlobalShortcuts } from './useGlobalShortcuts'

/**
 * The frame every signed-in page sits in: a top bar, an icon rail (a bottom bar on a phone), and
 * the page. It also owns what the pages share: the data, the search palette and the shortcuts.
 */
export function Shell() {
  const navigate = useNavigate()
  const location = useLocation()
  const [paletteOpen, setPaletteOpen] = useState(false)

  const openPalette = useCallback(() => setPaletteOpen(true), [])
  const closePalette = useCallback(() => setPaletteOpen(false), [])

  const pickSymbol = useCallback(
    (symbol: string) => {
      rememberSymbol(symbol)
      // Stay on the charts page if that is where the user is; otherwise go to the dashboard.
      navigate({ pathname: location.pathname === '/charts' ? '/charts' : '/', search: `?symbol=${encodeURIComponent(symbol)}` })
    },
    [navigate, location.pathname],
  )

  const openTicket = useCallback(
    (side: 'BUY' | 'SELL') => {
      const current = location.pathname === '/' ? new URLSearchParams(location.search).get('symbol') : null
      const params = new URLSearchParams()
      const symbol = current ?? rememberedSymbol()
      if (symbol !== null) {
        params.set('symbol', symbol)
      }
      params.set('side', side)
      navigate({ pathname: '/', search: `?${params.toString()}` })
    },
    [navigate, location.pathname, location.search],
  )

  useGlobalShortcuts({ onSearch: openPalette, onSide: openTicket })

  const shell = useMemo<ShellContextValue>(() => ({ openPalette, pickSymbol }), [openPalette, pickSymbol])

  return (
    <TradingDataProvider>
      <ShellContext.Provider value={shell}>
        <div className="flex h-dvh flex-col bg-canvas text-ink">
          <a href="#main" className="skip-link">
            Skip to content
          </a>
          <TopBar onOpenPalette={openPalette} />
          <div className="flex min-h-0 flex-1">
            <Sidebar />
            <main id="main" className="min-h-0 min-w-0 flex-1 overflow-hidden">
              <Outlet />
            </main>
          </div>
          <BottomNav />
        </div>
        <CommandPalette open={paletteOpen} onClose={closePalette} onPickSymbol={pickSymbol} />
      </ShellContext.Provider>
    </TradingDataProvider>
  )
}
