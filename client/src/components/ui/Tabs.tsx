import { useId, useRef, useState } from 'react'
import type { KeyboardEvent, ReactNode } from 'react'

export interface TabItem {
  id: string
  label: string
  /** A small number beside the label, e.g. how many rows are inside. */
  count?: number
  content: ReactNode
}

interface TabsProps {
  items: TabItem[]
  label: string
  /** Which tab is open when nothing has been chosen yet. */
  initial?: string
  /** Set to open a tab from outside (the tab is still chosen by the user afterwards). */
  requested?: string | null
  className?: string
}

/**
 * Tabs the way a screen reader expects them: a tablist, one tab in the tab order at a time,
 * arrow keys and Home/End to move between them, and only the open panel in the page.
 */
export function Tabs({ items, label, initial, requested, className = '' }: TabsProps) {
  const base = useId()
  const [chosen, setChosen] = useState(initial ?? items[0]?.id)
  const [seenRequest, setSeenRequest] = useState<string | null | undefined>(requested)
  const buttons = useRef<Record<string, HTMLButtonElement | null>>({})

  // An outside request opens its tab once, when it changes (state that follows a prop, set while rendering).
  if (requested !== seenRequest) {
    setSeenRequest(requested)
    if (requested !== null && requested !== undefined && items.some((item) => item.id === requested)) {
      setChosen(requested)
    }
  }

  const activeId = items.some((item) => item.id === chosen) ? chosen : items[0]?.id
  const active = items.find((item) => item.id === activeId)

  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    const position = items.findIndex((item) => item.id === activeId)
    const target: Record<string, number> = {
      ArrowRight: (position + 1) % items.length,
      ArrowLeft: (position - 1 + items.length) % items.length,
      Home: 0,
      End: items.length - 1,
    }
    const next = target[event.key]
    if (next === undefined) {
      return
    }
    event.preventDefault()
    setChosen(items[next].id)
    buttons.current[items[next].id]?.focus()
  }

  return (
    <div className={`flex min-h-0 min-w-0 flex-col ${className}`}>
      <div role="tablist" aria-label={label} onKeyDown={onKeyDown} className="flex shrink-0 overflow-x-auto border-b border-rule">
        {items.map((item) => (
          <button
            key={item.id}
            ref={(element) => {
              buttons.current[item.id] = element
            }}
            id={`${base}-tab-${item.id}`}
            role="tab"
            type="button"
            aria-selected={item.id === activeId}
            aria-controls={`${base}-panel-${item.id}`}
            tabIndex={item.id === activeId ? 0 : -1}
            onClick={() => setChosen(item.id)}
            className={`-mb-px flex min-h-8 shrink-0 items-center gap-1.5 border-b-2 px-3 text-sm font-medium transition-colors duration-150 pointer-coarse:min-h-11 ${
              item.id === activeId ? 'border-accent-text text-ink' : 'border-transparent text-ink-3 hover:text-ink'
            }`}
          >
            {item.label}
            {item.count !== undefined && <span className="rounded-full bg-selected px-1.5 text-xs text-ink-2">{item.count}</span>}
          </button>
        ))}
      </div>
      {active !== undefined && (
        <div
          id={`${base}-panel-${active.id}`}
          role="tabpanel"
          aria-labelledby={`${base}-tab-${active.id}`}
          className="min-h-0 flex-1 overflow-auto"
        >
          {active.content}
        </div>
      )}
    </div>
  )
}
