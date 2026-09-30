import { useSyncExternalStore } from 'react'

/** Whether a CSS media query currently matches, kept live as the window resizes. */
export function useMediaQuery(query: string): boolean {
  return useSyncExternalStore(
    (notify) => {
      const list = window.matchMedia(query)
      list.addEventListener('change', notify)
      return () => list.removeEventListener('change', notify)
    },
    () => window.matchMedia(query).matches,
    () => false,
  )
}

/** The workspace splits into resizable panels from this width up; below it, panels become tabs. */
export const useIsDesktop = () => useMediaQuery('(min-width: 900px)')
