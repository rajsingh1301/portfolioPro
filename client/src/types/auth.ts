/** Mirrors com.portfoliopro.auth.dto.UserResponse. */
export interface User {
  id: number
  email: string
  /** A string, not a number: money must never be parsed into a float. */
  cashBalance: string
}

/** Mirrors com.portfoliopro.auth.dto.AuthResponse. */
export interface AuthResponse {
  token: string
  tokenType: string
  expiresInMinutes: number
  user: User
}

export interface Credentials {
  email: string
  password: string
}

/** The single error shape the API returns, from com.portfoliopro.common.ApiError. */
export interface ApiError {
  timestamp: string
  status: number
  code: string
  message: string
  path: string
  fieldErrors?: Record<string, string>
}
