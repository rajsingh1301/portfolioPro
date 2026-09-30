import { Navigate, Outlet, useLocation } from 'react-router-dom'

import { useAuth } from '../context/useAuth'

/**
 * Waits for the stored token to be checked before deciding. Without that wait a
 * refresh on a protected page would bounce to login before /me has answered.
 */
export function ProtectedRoute() {
  const { isAuthenticated, isLoading } = useAuth()
  const location = useLocation()

  if (isLoading) {
    return (
      <div role="status" aria-live="polite" className="flex min-h-dvh items-center justify-center text-sm text-ink-3">
        Checking your session…
      </div>
    )
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }

  return <Outlet />
}
