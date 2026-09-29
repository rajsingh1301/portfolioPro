import axios, { AxiosError } from 'axios'

import type { ApiError } from '../types/auth'

const TOKEN_STORAGE_KEY = 'portfoliopro.token'

export function getStoredToken(): string | null {
  return localStorage.getItem(TOKEN_STORAGE_KEY)
}

export function setStoredToken(token: string | null): void {
  if (token === null) {
    localStorage.removeItem(TOKEN_STORAGE_KEY)
  } else {
    localStorage.setItem(TOKEN_STORAGE_KEY, token)
  }
}

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  headers: { 'Content-Type': 'application/json' },
})

api.interceptors.request.use((config) => {
  const token = getStoredToken()
  if (token !== null) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/**
 * AuthContext registers here so a 401 anywhere in the app can clear the session.
 * Kept as a callback rather than a redirect so routing stays React Router's job.
 */
let onUnauthorized: (() => void) | null = null

export function setUnauthorizedHandler(handler: (() => void) | null): void {
  onUnauthorized = handler
}

api.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiError>) => {
    // A failed login is a 401 too, but it is the caller's to report, not a
    // reason to tear down a session that was never established.
    const isCredentialsCheck = error.response?.data?.code === 'INVALID_CREDENTIALS'
    if (error.response?.status === 401 && !isCredentialsCheck) {
      setStoredToken(null)
      onUnauthorized?.()
    }
    return Promise.reject(error)
  },
)

/** Turns any axios failure into something safe to show a user. */
export function errorMessage(error: unknown, fallback = 'Something went wrong'): string {
  if (axios.isAxiosError<ApiError>(error)) {
    const data = error.response?.data
    if (data?.fieldErrors !== undefined) {
      const first = Object.values(data.fieldErrors)[0]
      if (first !== undefined) {
        return first
      }
    }
    if (data?.message !== undefined) {
      return data.message
    }
    if (error.code === 'ERR_NETWORK') {
      return 'Cannot reach the server. Is the backend running?'
    }
  }
  return fallback
}
