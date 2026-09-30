import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'

import { errorMessage } from '../api/client'
import { AuthLayout } from '../components/AuthLayout'
import { FormField } from '../components/FormField'
import { useAuth } from '../context/useAuth'

export function Signup() {
  const { signup, isAuthenticated } = useAuth()
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
      await signup({ email, password })
      navigate('/', { replace: true })
    } catch (caught) {
      setError(errorMessage(caught, 'Could not sign up'))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <AuthLayout
      title="Create an account"
      subtitle="You start with $100,000 of virtual cash. No card, no real money."
      footer={
        <>
          Already have an account?{' '}
          <Link to="/login" className="font-medium text-accent underline underline-offset-4 hover:text-accent-strong">
            Log in
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
          autoComplete="new-password"
          onChange={setPassword}
          hint="At least 8 characters."
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
          {isSubmitting ? 'Creating account...' : 'Create account'}
        </button>
      </form>
    </AuthLayout>
  )
}
