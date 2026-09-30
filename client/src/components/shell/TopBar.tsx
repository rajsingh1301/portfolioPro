import { Link } from 'react-router-dom'

import { useTheme } from '../../context/useTheme'
import { Icon } from '../ui/Icon'
import { ProfileMenu } from './ProfileMenu'

/** Logo, the search box (which opens the command palette), the theme switch and the account. */
export function TopBar({ onOpenPalette }: { onOpenPalette: () => void }) {
  const { theme, toggle } = useTheme()
  const next = theme === 'dark' ? 'light' : 'dark'
  return (
    <header className="flex h-10 shrink-0 items-center gap-3 border-b border-rule bg-panel px-3 pointer-coarse:h-12">
      <Link to="/" className="inline-flex items-center text-base font-semibold tracking-tight text-ink pointer-coarse:min-h-11" aria-label="PortfolioPro, home">
        PortfolioPro
      </Link>

      <button
        type="button"
        onClick={onOpenPalette}
        aria-label="Search symbols and commands"
        aria-keyshortcuts="Control+K /"
        className="flex h-7 min-w-0 flex-1 items-center gap-2 rounded-control border border-edge bg-field px-2 text-left text-sm text-ink-3 transition-colors duration-150 hover:bg-hover sm:max-w-sm pointer-coarse:h-11"
      >
        <Icon name="search" size={14} className="shrink-0" />
        <span className="min-w-0 flex-1 truncate">Search symbols</span>
        <span className="hidden gap-1 sm:flex">
          <kbd className="kbd">Ctrl</kbd>
          <kbd className="kbd">K</kbd>
        </span>
      </button>

      <div className="ml-auto flex items-center gap-1">
        <button
          type="button"
          onClick={toggle}
          aria-label={`Switch to the ${next} theme`}
          title={`Switch to the ${next} theme`}
          className="btn btn-quiet size-7 px-0 pointer-coarse:size-11"
        >
          <Icon name={theme === 'dark' ? 'sun' : 'moon'} size={16} />
        </button>
        <ProfileMenu />
      </div>
    </header>
  )
}
