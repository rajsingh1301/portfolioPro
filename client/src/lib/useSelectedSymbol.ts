import { useCallback } from 'react'
import { useSearchParams } from 'react-router-dom'

const STORAGE_KEY = 'pp-symbol'
const VALID = /^[A-Z0-9.-]{1,20}$/
const FALLBACK = 'AAPL'

/** The symbol last looked at, if storage is available and holds a real one. */
export function rememberedSymbol(): string | null {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    return saved !== null && VALID.test(saved) ? saved : null
  } catch {
    return null
  }
}

export function rememberSymbol(symbol: string): void {
  try {
    localStorage.setItem(STORAGE_KEY, symbol)
  } catch {
    // Not remembered across visits; the URL still carries it for this one.
  }
}

/**
 * The symbol on screen. It lives in the URL (`?symbol=`), so a link, a reload and the back button
 * all keep it; with none in the URL it is the last one looked at, then AAPL.
 */
export function useSelectedSymbol() {
  const [params, setParams] = useSearchParams()
  const fromUrl = params.get('symbol')?.toUpperCase() ?? null
  const symbol = fromUrl !== null && VALID.test(fromUrl) ? fromUrl : (rememberedSymbol() ?? FALLBACK)

  const select = useCallback(
    (next: string) => {
      const chosen = next.toUpperCase()
      if (!VALID.test(chosen)) {
        return
      }
      rememberSymbol(chosen)
      setParams((previous) => {
        const updated = new URLSearchParams(previous)
        updated.set('symbol', chosen)
        updated.delete('side')
        return updated
      })
    },
    [setParams],
  )

  return { symbol, select }
}
