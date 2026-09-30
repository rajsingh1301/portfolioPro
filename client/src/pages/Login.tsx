import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'

import { errorMessage } from '../api/client'
import { AuthLayout } from '../components/AuthLayout'
import { FormField } from '../components/FormField'
import { useAuth } from '../context/useAuth'

export function Login() {
  const { login, isAuthenticated } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  if (isAuthenticated) {
    return <Navigate to="/" replace />
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setError(null)
    setIsSubmitting(true)
    try {
      await login({ email, password })
      navigate('/', { replace: true })
    } catch (caught) {
      setError(errorMessage(caught, 'Could not log in'))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <AuthLayout
      title="Log in"
      subtitle="Pick up where you left off."
      footer={
        <>
          No account yet?{' '}
          <Link to="/signup" className="font-medium text-accent underline underline-offset-4 hover:text-accent-strong">
            Sign up
          </Link>
        </>
      }
    >
      <form onSubmit={handleSubmit} className="mt-8 space-y-5">
        <FormField
          id="email"
          label="Email"
          type="email"
          value={email}
          autoComplete="email"
          onChange={setEmail}
        />
        <FormField
          id="password"
          label="Password"
          type="password"
          value={password}
          autoComplete="current-password"
          onChange={setPassword}
        />
        {error !== null && (
          <p role="alert" className="notice-error">
            {error}
          </p>
        )}
        <button
          type="submit"
          disabled={isSubmitting}
          className="btn btn-primary w-full"
        >
          {isSubmitting ? 'Logging in...' : 'Log in'}
        </button>
      </form>
    </AuthLayout>
  )
}
