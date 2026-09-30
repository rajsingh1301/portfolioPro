import { useEffect } from 'react'

/** Whether a key press is going into a text field, where a shortcut letter must just be typed. */
function isTyping(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) {
    return false
  }
  return target.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName)
}

interface Handlers {
  onSearch: () => void
  onSide: (side: 'BUY' | 'SELL') => void
}

/**
 * Ctrl or Cmd + K, and /, open the search; B and S open the order ticket on the buy or sell side.
 * The single letters are ignored while typing in a field, while a dialog is open, and whenever a
 * modifier key is held, so they never fight the browser's own shortcuts.
 */
export function useGlobalShortcuts({ onSearch, onSide }: Handlers) {
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault()
        onSearch()
        return
      }
      if (event.ctrlKey || event.metaKey || event.altKey || event.defaultPrevented) {
        return
      }
      if (isTyping(event.target) || document.querySelector('dialog[open]') !== null) {
        return
      }
      if (event.key === '/') {
        event.preventDefault()
        onSearch()
      } else if (event.key === 'b' || event.key === 'B') {
        onSide('BUY')
      } else if (event.key === 's' || event.key === 'S') {
        onSide('SELL')
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onSearch, onSide])
}
