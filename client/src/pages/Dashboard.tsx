import { useAuth } from '../context/useAuth'
import { formatUsd } from '../lib/money'

export function Dashboard() {
  const { user, logout } = useAuth()

  if (user === null) {
    return null
  }

  return (
    <div className="min-h-dvh bg-slate-100">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-4xl items-center justify-between px-6 py-4">
          <span className="font-semibold text-slate-900">PortfolioPro</span>
          <div className="flex items-center gap-4">
            <span className="text-sm text-slate-500">{user.email}</span>
            <button
              type="button"
              onClick={logout}
              className="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 transition hover:bg-slate-50"
            >
              Log out
            </button>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-4xl px-6 py-10">
        <h1 className="text-2xl font-semibold text-slate-900">Dashboard</h1>

        <div className="mt-6 rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <p className="text-sm font-medium text-slate-500">Available cash</p>
          <p className="mt-1 text-3xl font-semibold tracking-tight text-slate-900">
            {formatUsd(user.cashBalance)}
          </p>
        </div>

        <p className="mt-6 text-sm text-slate-500">
          Market data and trading arrive in the next slices.
        </p>
      </main>
    </div>
  )
}
