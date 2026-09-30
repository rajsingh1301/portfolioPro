import type { ReactNode } from 'react'

/** Flat placeholder rows while a table's first load is in flight. */
export function SkeletonRows({ rows = 5, columns = 4 }: { rows?: number; columns?: number }) {
  return (
    <div role="status" aria-label="Loading" className="space-y-2 p-2.5">
      {Array.from({ length: rows }, (_, row) => (
        <div key={row} className="flex gap-3">
          {Array.from({ length: columns }, (_, column) => (
            <div key={column} className={`skeleton h-3.5 ${column === 0 ? 'w-16' : 'flex-1'}`} />
          ))}
        </div>
      ))}
    </div>
  )
}

/** A failed request: what happened, and a way to try again in the same place. */
export function ErrorState({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div role="alert" className="flex flex-col items-start gap-2 p-3 text-sm">
      <p className="notice-error">{message}</p>
      <button type="button" onClick={onRetry} className="btn">
        Retry
      </button>
    </div>
  )
}

/** Nothing here yet, and what to do about it. */
export function EmptyState({ children }: { children: ReactNode }) {
  return <p className="max-w-sm p-3 text-sm text-ink-2">{children}</p>
}
