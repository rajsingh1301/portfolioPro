import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'

import { useAuth } from '../../context/useAuth'

/** The account button: who you are, the risk limits, and log out. Esc or a click elsewhere closes it. */
export function ProfileMenu() {
  const { user, logout } = useAuth()
  const [open, setOpen] = useState(false)
  const root = useRef<HTMLDivElement>(null)
  const button = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    if (!open) {
      return
    }
    const onPointer = (event: PointerEvent) => {
      if (root.current !== null && !root.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setOpen(false)
        button.current?.focus()
      }
    }
    document.addEventListener('pointerdown', onPointer)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('pointerdown', onPointer)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  if (user === null) {
    return null
  }

  return (
    <div ref={root} className="relative">
      <button
        ref={button}
        type="button"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label="Account"
        onClick={() => setOpen((current) => !current)}
        className="flex size-7 items-center justify-center rounded-full border border-edge bg-selected text-xs font-semibold uppercase text-ink transition-colors duration-150 hover:bg-hover pointer-coarse:size-11"
      >
        {user.email.slice(0, 1)}
      </button>
      {open && (
        <div role="menu" className="absolute right-0 top-full z-30 mt-1 w-56 rounded-control border border-edge bg-panel py-1 text-sm">
          <p className="truncate border-b border-rule px-3 py-2 text-ink-2" title={user.email}>
            {user.email}
          </p>
          <Link
            role="menuitem"
            to="/risk"
            onClick={() => setOpen(false)}
            className="flex min-h-8 items-center px-3 text-ink hover:bg-hover"
          >
            Risk limits
          </Link>
          <button
            role="menuitem"
            type="button"
            onClick={logout}
            className="flex min-h-8 w-full items-center px-3 text-left text-ink hover:bg-hover"
          >
            Log out
          </button>
        </div>
      )}
    </div>
  )
}
