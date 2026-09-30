import { createContext, useContext } from 'react'

export interface ShellContextValue {
  openPalette: () => void
  /** Selects a symbol on the trading screen, going there first if the user is elsewhere. */
  pickSymbol: (symbol: string) => void
}

export const ShellContext = createContext<ShellContextValue | null>(null)

export function useShell(): ShellContextValue {
  const value = useContext(ShellContext)
  if (value === null) {
    throw new Error('useShell must be used inside the Shell')
  }
  return value
}
