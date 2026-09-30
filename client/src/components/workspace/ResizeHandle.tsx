import { Separator } from 'react-resizable-panels'

/**
 * The seam between two panels: a 1px hairline with a wider invisible hit area, so it is easy to
 * grab. It is keyboard-operable (focus it and use the arrow keys), and lights up in the accent
 * colour when hovered, focused or dragged.
 *
 * @param orientation the direction the seam runs: a vertical seam sits between side-by-side panels
 */
export function ResizeHandle({ orientation }: { orientation: 'vertical' | 'horizontal' }) {
  const vertical = orientation === 'vertical'
  return (
    <Separator
      className={`relative shrink-0 bg-rule transition-colors duration-150 hover:bg-accent-text focus-visible:bg-accent-text data-[separator=active]:bg-accent-text ${
        vertical ? 'w-px cursor-col-resize' : 'h-px cursor-row-resize'
      } after:absolute after:content-[''] ${vertical ? 'after:-inset-x-1 after:inset-y-0' : 'after:-inset-y-1 after:inset-x-0'}`}
    />
  )
}
