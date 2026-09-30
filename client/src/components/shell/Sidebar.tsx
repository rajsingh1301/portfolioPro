import { NavLink } from 'react-router-dom'

import { Icon } from '../ui/Icon'
import { NAV_ITEMS, RISK_ITEM } from './nav'

/** A thin icon rail. Each icon is a link with a real name, shown as a tooltip and read aloud. */
export function Sidebar() {
  return (
    <nav aria-label="Main" className="hidden w-11 shrink-0 flex-col items-center gap-1 border-r border-rule bg-panel py-2 md:flex">
      {NAV_ITEMS.map((item) => (
        <RailLink key={item.to} {...item} />
      ))}
      <div className="mt-auto">
        <RailLink {...RISK_ITEM} />
      </div>
    </nav>
  )
}

function RailLink({ to, label, icon, end }: { to: string; label: string; icon: (typeof NAV_ITEMS)[number]['icon']; end?: boolean }) {
  return (
    <NavLink
      to={to}
      end={end}
      aria-label={label}
      title={label}
      className={({ isActive }) =>
        `relative flex size-9 items-center justify-center rounded-control transition-colors duration-150 hover:bg-hover hover:text-ink ${
          isActive ? 'bg-selected text-ink before:absolute before:inset-y-1.5 before:-left-1 before:w-0.5 before:bg-accent-text' : 'text-ink-3'
        }`
      }
    >
      <Icon name={icon} />
    </NavLink>
  )
}
