import type { IconName } from '../ui/Icon'

export interface NavItem {
  to: string
  label: string
  icon: IconName
  /** Match only this exact path, so "/" is not lit on every page. */
  end?: boolean
}

export const NAV_ITEMS: NavItem[] = [
  { to: '/', label: 'Dashboard', icon: 'dashboard', end: true },
  { to: '/charts', label: 'Charts', icon: 'charts' },
  { to: '/portfolio', label: 'Portfolio', icon: 'portfolio' },
  { to: '/orders', label: 'Orders', icon: 'orders' },
  { to: '/watchlist', label: 'Watchlist', icon: 'watchlist' },
]

export const RISK_ITEM: NavItem = { to: '/risk', label: 'Risk limits', icon: 'risk' }
