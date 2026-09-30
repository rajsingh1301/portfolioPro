const PATHS = {
  dashboard: (
    <>
      <rect x="3" y="3" width="7" height="7" />
      <rect x="14" y="3" width="7" height="7" />
      <rect x="3" y="14" width="7" height="7" />
      <rect x="14" y="14" width="7" height="7" />
    </>
  ),
  charts: (
    <>
      <path d="M7 3v4M7 17v4M17 7v3M17 18v3" />
      <rect x="4.5" y="7" width="5" height="10" />
      <rect x="14.5" y="10" width="5" height="8" />
    </>
  ),
  portfolio: (
    <>
      <path d="M12 3v9h9" />
      <path d="M20.5 15.5A9 9 0 1 1 8.5 3.5" />
    </>
  ),
  orders: <path d="M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01" />,
  watchlist: <path d="M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z" />,
  risk: (
    <>
      <path d="M3 6h9M18 6h3M3 12h3M12 12h9M3 18h11M20 18h1" />
      <circle cx="15" cy="6" r="2" />
      <circle cx="9" cy="12" r="2" />
      <circle cx="17" cy="18" r="2" />
    </>
  ),
  search: (
    <>
      <circle cx="11" cy="11" r="6.5" />
      <path d="M16 16l5 5" />
    </>
  ),
  sun: (
    <>
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2v2M12 20v2M2 12h2M20 12h2M5 5l1.5 1.5M17.5 17.5L19 19M5 19l1.5-1.5M17.5 6.5L19 5" />
    </>
  ),
  moon: <path d="M20 14.5A8.5 8.5 0 0 1 9.5 4 8.5 8.5 0 1 0 20 14.5z" />,
  chevronDown: <path d="M6 9l6 6 6-6" />,
  panelRight: (
    <>
      <rect x="3" y="4" width="18" height="16" />
      <path d="M15 4v16" />
    </>
  ),
  panelBottom: (
    <>
      <rect x="3" y="4" width="18" height="16" />
      <path d="M3 14h18" />
    </>
  ),
} as const

export type IconName = keyof typeof PATHS

/** A 1.75px-stroke line icon. Decorative: the button or link around it carries the name. */
export function Icon({ name, size = 18, className = '' }: { name: IconName; size?: number; className?: string }) {
  return (
    <svg
      aria-hidden
      focusable="false"
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.75"
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
    >
      {PATHS[name]}
    </svg>
  )
}
