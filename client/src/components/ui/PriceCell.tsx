import { useState } from 'react'

import { formatUsd } from '../../lib/money'

/**
 * A price. When the number changes it is tinted for a second, green if it rose and red if it
 * fell, and the tint fades away. The cell is re-created on each change (its key is the value), so
 * the animation restarts every time. The tint is decoration: which way it moved is also said by
 * the arrows and signs next to it, and `prefers-reduced-motion` switches the fade off.
 */
export function PriceCell({ value, className = '' }: { value: string | undefined; className?: string }) {
  // Adjusting state while rendering, when the prop changes, is React's own pattern for this
  // ("storing information from previous renders"): no effect, and no flash on the first render.
  const [previous, setPrevious] = useState(value)
  const [flash, setFlash] = useState<'up' | 'down' | null>(null)
  if (value !== previous) {
    setPrevious(value)
    if (value !== undefined && previous !== undefined && Number(value) !== Number(previous)) {
      setFlash(Number(value) > Number(previous) ? 'up' : 'down')
    }
  }

  return (
    <span key={value} className={`inline-block rounded-control px-1 ${flash === null ? '' : `flash-${flash}`} ${className}`}>
      {value === undefined ? '—' : formatUsd(value)}
    </span>
  )
}
