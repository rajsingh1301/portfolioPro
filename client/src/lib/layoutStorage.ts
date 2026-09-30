import type { LayoutStorage } from 'react-resizable-panels'

/**
 * Where panel sizes are remembered. Storage can be blocked (a private window, cleared site data),
 * and a throw from it must not take the workspace down, so every call is guarded and a failure
 * just means the layout is not remembered.
 */
export const layoutStorage: LayoutStorage = {
  getItem(key: string) {
    try {
      return localStorage.getItem(key)
    } catch {
      return null
    }
  },
  setItem(key: string, value: string) {
    try {
      localStorage.setItem(key, value)
    } catch {
      // Not remembered; the layout itself still works.
    }
  },
}
