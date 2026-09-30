import { useCallback, useState } from 'react'
import type { RefObject } from 'react'
import type { PanelImperativeHandle } from 'react-resizable-panels'

/**
 * Collapse and expand for one panel, and whether it is collapsed now (which changes both by the
 * buttons and by dragging its edge past its minimum size). Pass `onResize` to the Panel.
 */
export function useCollapsible(panelRef: RefObject<PanelImperativeHandle | null>) {
  const [collapsed, setCollapsed] = useState(false)

  const onResize = useCallback(() => {
    setCollapsed(panelRef.current?.isCollapsed() ?? false)
  }, [panelRef])

  const toggle = useCallback(() => {
    const panel = panelRef.current
    if (panel === null) {
      return
    }
    if (panel.isCollapsed()) {
      panel.expand()
    } else {
      panel.collapse()
    }
  }, [panelRef])

  return { collapsed, onResize, toggle }
}
