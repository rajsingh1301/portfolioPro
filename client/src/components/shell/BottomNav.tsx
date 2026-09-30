import { NavLink } from 'react-router-dom'

import { Icon } from '../ui/Icon'
import { NAV_ITEMS } from './nav'

/** On a phone the icon rail becomes a bar along the bottom, with a label under each icon. */
export function BottomNav() {
  return (
    <nav aria-label="Main" className="flex shrink-0 border-t border-rule bg-panel md:hidden">
      {NAV_ITEMS.map((item) => (
        <NavLink
          key={item.to}
          to={item.to}
          end={item.end}
          className={({ isActive }) =>
            `flex min-h-12 flex-1 flex-col items-center justify-center gap-0.5 text-xs transition-colors duration-150 ${
              isActive ? 'bg-selected text-ink' : 'text-ink-3 hover:bg-hover hover:text-ink'
            }`
          }
        >
          <Icon name={item.icon} size={18} />
          {item.label}
        </NavLink>
      ))}
    </nav>
  )
}
