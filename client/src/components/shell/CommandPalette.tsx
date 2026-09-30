import { useEffect, useId, useMemo, useRef, useState } from 'react'
import type { KeyboardEvent } from 'react'
import { useNavigate } from 'react-router-dom'

import { errorMessage } from '../../api/client'
import { searchStocks } from '../../api/trading'
import { useTheme } from '../../context/useTheme'
import { useTradingData } from '../../data/useTradingData'
import type { StockSearchResult } from '../../types/trading'
import { Icon } from '../ui/Icon'
import { NAV_ITEMS, RISK_ITEM } from './nav'

interface Item {
  id: string
  group: 'Symbols' | 'Go to' | 'Actions'
  label: string
  hint?: string
  run: () => void
}

/** Wait this long after the last keystroke before asking Finnhub, which is rate-limited. */
const DEBOUNCE_MS = 300

interface CommandPaletteProps {
  open: boolean
  onClose: () => void
  onPickSymbol: (symbol: string) => void
}

/**
 * Ctrl+K (or /). Type a company or ticker to open it, or a page name to go there. A native
 * `<dialog>`, so the browser traps focus inside it, closes it on Esc, and returns focus to where
 * it came from. Its contents are mounted only while it is open, so it starts fresh every time.
 */
export function CommandPalette({ open, onClose, onPickSymbol }: CommandPaletteProps) {
  const dialog = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    const element = dialog.current
    if (element === null) {
      return
    }
    if (open && !element.open) {
      element.showModal()
    } else if (!open && element.open) {
      element.close()
    }
  }, [open])

  return (
    <dialog
      ref={dialog}
      aria-label="Search symbols and commands"
      onClose={onClose}
      // A click on the backdrop lands on the dialog element itself, not on anything inside it.
      onClick={(event) => {
        if (event.target === dialog.current) {
          onClose()
        }
      }}
      className="m-0 mx-auto mt-[12vh] w-[min(36rem,calc(100vw-1.5rem))] overflow-hidden rounded-control border border-edge bg-panel p-0 text-ink backdrop:bg-black/50"
    >
      {open && <PaletteBody onClose={onClose} onPickSymbol={onPickSymbol} />}
    </dialog>
  )
}

function PaletteBody({ onClose, onPickSymbol }: Pick<CommandPaletteProps, 'onClose' | 'onPickSymbol'>) {
  const navigate = useNavigate()
  const { theme, toggle } = useTheme()
  const { watchlist } = useTradingData()
  const [query, setQuery] = useState('')
  const [active, setActive] = useState(0)
  const [remote, setRemote] = useState<{ query: string; results: StockSearchResult[]; error: string | null } | null>(null)
  const listId = useId()
  const trimmed = query.trim()

  // Search upstream, after a pause, for anything typed that is at least two characters.
  useEffect(() => {
    if (trimmed.length < 2) {
      return
    }
    let current = true
    const timer = window.setTimeout(() => {
      searchStocks(trimmed)
        .then((results) => current && setRemote({ query: trimmed, results, error: null }))
        .catch((failure: unknown) => current && setRemote({ query: trimmed, results: [], error: errorMessage(failure, 'Search failed') }))
    }, DEBOUNCE_MS)
    return () => {
      current = false
      window.clearTimeout(timer)
    }
  }, [trimmed])

  const items = useMemo<Item[]>(() => {
    const lower = trimmed.toLowerCase()
    const pick = (symbol: string) => () => onPickSymbol(symbol)
    const seen = new Set<string>()
    const symbols: Item[] = []
    const add = (symbol: string, name?: string, hint?: string) => {
      if (!seen.has(symbol)) {
        seen.add(symbol)
        symbols.push({ id: `sym-${symbol}`, group: 'Symbols', label: symbol, hint: name ?? hint, run: pick(symbol) })
      }
    }
    for (const item of watchlist.data ?? []) {
      if (lower === '' || item.symbol.toLowerCase().includes(lower) || (item.name ?? '').toLowerCase().includes(lower)) {
        add(item.symbol, item.name, 'On your watchlist')
      }
    }
    if (remote !== null && remote.query === trimmed) {
      remote.results.forEach((result) => add(result.symbol, result.name))
    }

    const pages: Item[] = [...NAV_ITEMS, RISK_ITEM]
      .filter((page) => lower === '' || page.label.toLowerCase().includes(lower))
      .map((page) => ({ id: `go-${page.to}`, group: 'Go to' as const, label: page.label, run: () => navigate(page.to) }))
    const actions: Item[] = []
    if (lower === '' || 'theme'.includes(lower) || 'dark'.includes(lower) || 'light'.includes(lower)) {
      actions.push({
        id: 'act-theme',
        group: 'Actions',
        label: `Switch to the ${theme === 'dark' ? 'light' : 'dark'} theme`,
        run: toggle,
      })
    }
    return [...symbols, ...pages, ...actions]
  }, [trimmed, remote, watchlist.data, navigate, onPickSymbol, theme, toggle])

  const searching = trimmed.length >= 2 && (remote === null || remote.query !== trimmed)
  const index = Math.min(active, Math.max(items.length - 1, 0))

  const run = (item: Item | undefined) => {
    if (item !== undefined) {
      onClose()
      item.run()
    }
  }

  const onKeyDown = (event: KeyboardEvent) => {
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      setActive(items.length === 0 ? 0 : (index + 1) % items.length)
    } else if (event.key === 'ArrowUp') {
      event.preventDefault()
      setActive(items.length === 0 ? 0 : (index - 1 + items.length) % items.length)
    } else if (event.key === 'Enter') {
      event.preventDefault()
      run(items[index])
    }
  }

  let lastGroup = ''
  return (
    <div>
      <div className="flex items-center gap-2 border-b border-rule px-3">
        <Icon name="search" size={16} className="shrink-0 text-ink-3" />
        <input
          autoFocus
          role="combobox"
          aria-expanded
          aria-controls={listId}
          aria-activedescendant={items[index] === undefined ? undefined : `${listId}-${items[index].id}`}
          aria-label="Search symbols and commands"
          value={query}
          onChange={(event) => {
            setQuery(event.target.value)
            setActive(0)
          }}
          onKeyDown={onKeyDown}
          placeholder="Search a company or ticker, or type a page name"
          className="min-h-11 w-full bg-transparent text-base text-ink outline-none placeholder:text-ink-3"
        />
        <kbd className="kbd">Esc</kbd>
      </div>

      <ul id={listId} role="listbox" aria-label="Results" className="max-h-[50vh] overflow-y-auto py-1">
        {items.map((item, position) => {
          const heading = item.group !== lastGroup ? item.group : null
          lastGroup = item.group
          return (
            <li key={item.id} role="presentation">
              {heading !== null && <p className="label px-3 pb-0.5 pt-2">{heading}</p>}
              <div
                id={`${listId}-${item.id}`}
                role="option"
                aria-selected={position === index}
                onClick={() => run(item)}
                onMouseMove={() => setActive(position)}
                className={`flex min-h-8 items-center justify-between gap-3 px-3 text-base pointer-coarse:min-h-11 ${
                  position === index ? 'bg-selected text-ink' : 'text-ink-2'
                }`}
              >
                <span className="font-medium">{item.label}</span>
                {item.hint !== undefined && <span className="truncate text-sm text-ink-3">{item.hint}</span>}
              </div>
            </li>
          )
        })}
      </ul>

      <p role="status" className="border-t border-rule px-3 py-1.5 text-xs text-ink-3">
        {remote !== null && remote.query === trimmed && remote.error !== null
          ? remote.error
          : searching
            ? 'Searching…'
            : items.length === 0
              ? 'Nothing matches. Try a ticker such as AAPL.'
              : '↑ ↓ to move, Enter to open'}
      </p>
    </div>
  )
}
