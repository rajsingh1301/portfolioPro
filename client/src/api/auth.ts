import { api } from './client'
import type { AuthResponse, Credentials, User } from '../types/auth'

export async function signup(credentials: Credentials): Promise<AuthResponse> {
  const { data } = await api.post<AuthResponse>('/auth/signup', credentials)
  return data
}

export async function login(credentials: Credentials): Promise<AuthResponse> {
  const { data } = await api.post<AuthResponse>('/auth/login', credentials)
  return data
}

export async function fetchCurrentUser(): Promise<User> {
  const { data } = await api.get<User>('/auth/me')
  return data
}
