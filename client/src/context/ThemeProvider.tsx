import { useCallback, useMemo, useState } from 'react'
import type { ReactNode } from 'react'

import { THEME_KEY, ThemeContext, readTheme } from './themeContext'
import type { Theme } from './themeContext'

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setTheme] = useState<Theme>(readTheme)

  const toggle = useCallback(() => {
    const next: Theme = theme === 'dark' ? 'light' : 'dark'
    // The attribute is set here, before the state changes, not in an effect afterwards: a chart
    // that re-reads its colours when `theme` changes must find the new CSS variables already in
    // place, and a child's effect runs before its parent's.
    document.documentElement.dataset.theme = next
    try {
      localStorage.setItem(THEME_KEY, next)
    } catch {
      // Not remembered across visits, but the switch itself still works.
    }
    setTheme(next)
  }, [theme])

  const value = useMemo(() => ({ theme, toggle }), [theme, toggle])
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}
