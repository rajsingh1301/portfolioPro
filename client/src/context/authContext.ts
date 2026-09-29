import { createContext } from 'react'

import type { Credentials, User } from '../types/auth'

export interface AuthContextValue {
  user: User | null
  /** True until the stored token has been checked, so routes do not redirect too early. */
  isLoading: boolean
  isAuthenticated: boolean
  signup: (credentials: Credentials) => Promise<void>
  login: (credentials: Credentials) => Promise<void>
  logout: () => void
  /** Re-reads the user, e.g. for the cash balance after a trade. */
  refreshUser: () => Promise<void>
}

/** Lives apart from the provider so the provider file only exports a component. */
export const AuthContext = createContext<AuthContextValue | null>(null)
