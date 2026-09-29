import { useCallback, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'

import { AuthContext } from './authContext'
import type { AuthContextValue } from './authContext'
import * as authApi from '../api/auth'
import { getStoredToken, setStoredToken, setUnauthorizedHandler } from '../api/client'
import type { Credentials, User } from '../types/auth'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  // Seeded from storage, so no render is spent loading when there is no token to check.
  const [isLoading, setIsLoading] = useState(() => getStoredToken() !== null)

  const clearSession = useCallback(() => {
    setStoredToken(null)
    setUser(null)
  }, [])

  // An expired or rejected token from any request ends the session here.
  useEffect(() => {
    setUnauthorizedHandler(clearSession)
    return () => setUnauthorizedHandler(null)
  }, [clearSession])

  // On a page refresh the token outlives the React state, so the user is re-fetched.
  useEffect(() => {
    if (getStoredToken() === null) {
      return
    }
    let active = true
    authApi
      .fetchCurrentUser()
      .then((fetched) => {
        if (active) {
          setUser(fetched)
        }
      })
      .catch(() => {
        // The interceptor has already dropped the token; nothing to add.
      })
      .finally(() => {
        if (active) {
          setIsLoading(false)
        }
      })
    return () => {
      active = false
    }
  }, [])

  const signup = useCallback(async (credentials: Credentials) => {
    const response = await authApi.signup(credentials)
    setStoredToken(response.token)
    setUser(response.user)
  }, [])

  const login = useCallback(async (credentials: Credentials) => {
    const response = await authApi.login(credentials)
    setStoredToken(response.token)
    setUser(response.user)
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isLoading,
      isAuthenticated: user !== null,
      signup,
      login,
      logout: clearSession,
    }),
    [user, isLoading, signup, login, clearSession],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
