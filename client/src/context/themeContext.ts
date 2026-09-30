import { createContext } from 'react'

export type Theme = 'dark' | 'light'

export interface ThemeContextValue {
  theme: Theme
  toggle: () => void
}

/** Lives apart from the provider so the provider file only exports a component. */
export const ThemeContext = createContext<ThemeContextValue | null>(null)

export const THEME_KEY = 'pp-theme'

export function readTheme(): Theme {
  try {
    const saved = localStorage.getItem(THEME_KEY)
    if (saved === 'light' || saved === 'dark') {
      return saved
    }
  } catch {
    // Storage can be blocked; dark is the default either way.
  }
  return 'dark'
}
