import axios from 'axios'
import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'

import { errorMessage } from '../api/client'
import { fetchRiskSettings, saveRiskSettings } from '../api/trading'
import { formatUsd } from '../lib/money'
import type { ApiError } from '../types/auth'
import type { LimitRange, RiskLimitsInput, RiskSettings } from '../types/trading'

type Field = keyof RiskLimitsInput

interface FieldSpec {
  id: Field
  label: string
  unit: '%' | '$'
  /** What the saved value means, in words, so the limit is never just a number. */
  meaning: (saved: string) => string
}

const FIELDS: FieldSpec[] = [
  {
    id: 'maxPositionPct',
    label: 'Max position size',
    unit: '%',
    meaning: (v) => `Reject a buy that would put more than ${Number(v)}% of your portfolio in one stock`,
  },
  {
    id: 'maxOrderValue',
    label: 'Max order value',
    unit: '$',
    meaning: (v) => `Reject any single order worth more than ${formatUsd(v)}`,
  },
  {
    id: 'defaultStopLossPct',
    label: 'Default stop-loss',
    unit: '%',
    meaning: (v) => `An attached stop-loss sits ${Number(v)}% below the fill price`,
  },
]

function rangeText(range: LimitRange, unit: '%' | '$'): string {
  const show = (v: string) => (unit === '$' ? formatUsd(v) : `${Number(v)}%`)
  return `${show(range.min)} to ${show(range.max)}`
}

export function RiskSettingsCard() {
  const [saved, setSaved] = useState<RiskSettings | null>(null)
  const [form, setForm] = useState<RiskLimitsInput | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<Field, string>>>({})
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    let active = true
    fetchRiskSettings()
      .then((loaded) => {
        if (active) {
          setSaved(loaded)
          setForm({
            maxPositionPct: loaded.maxPositionPct,
            maxOrderValue: loaded.maxOrderValue,
            defaultStopLossPct: loaded.defaultStopLossPct,
          })
        }
      })
      .catch((failure: unknown) => {
        if (active) {
          setError(errorMessage(failure, 'Could not load your risk limits'))
        }
      })
    return () => {
      active = false
    }
  }, [])

  async function submit(limits: RiskLimitsInput) {
    setBusy(true)
    setError(null)
    setNotice(null)
    setFieldErrors({})
    try {
      const updated = await saveRiskSettings(limits)
      setSaved(updated)
      setForm({
        maxPositionPct: updated.maxPositionPct,
        maxOrderValue: updated.maxOrderValue,
        defaultStopLossPct: updated.defaultStopLossPct,
      })
      setNotice('Saved. New limits apply to your next order, and to pending orders when they trigger.')
    } catch (failure) {
      // The server names the field that failed and what is allowed, so show it beside the field.
      if (axios.isAxiosError<ApiError>(failure) && failure.response?.data.fieldErrors !== undefined) {
        setFieldErrors(failure.response.data.fieldErrors as Partial<Record<Field, string>>)
      } else {
        setError(errorMessage(failure, 'Could not save your risk limits'))
      }
    } finally {
      setBusy(false)
    }
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (form !== null) {
      void submit(form)
    }
  }

  function reset() {
    if (saved !== null) {
      void submit(saved.defaults)
    }
  }

  const dirty =
    saved !== null &&
    form !== null &&
    FIELDS.some((field) => Number(form[field.id]) !== Number(saved[field.id]) || form[field.id].trim() === '')
  const atDefaults =
    saved !== null && FIELDS.every((field) => Number(saved[field.id]) === Number(saved.defaults[field.id]))

  return (
    <section className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
      <h2 className="text-lg font-semibold text-slate-900">Risk limits</h2>
      <p className="mt-1 text-sm text-slate-500">
        Checked before every order. A rejected order is kept in your history with the reason.
      </p>

      {saved === null || form === null ? (
        <p role={error === null ? 'status' : 'alert'} className={`mt-3 text-sm ${error === null ? 'text-slate-500' : 'text-red-600'}`}>
          {error ?? 'Loading…'}
        </p>
      ) : (
        <form onSubmit={handleSubmit} className="mt-4 space-y-4">
          {FIELDS.map((field) => {
            const message = fieldErrors[field.id]
            return (
              <div key={field.id}>
                <label htmlFor={`risk-${field.id}`} className="block text-sm font-medium text-slate-700">
                  {field.label}
                </label>
                <div className="mt-1 flex items-center gap-2">
                  {field.unit === '$' && <span className="text-slate-500">$</span>}
                  <input
                    id={`risk-${field.id}`}
                    inputMode="decimal"
                    value={form[field.id]}
                    onChange={(event) => setForm({ ...form, [field.id]: event.target.value })}
                    aria-invalid={message !== undefined}
                    aria-describedby={`risk-${field.id}-hint`}
                    className="w-40 rounded-md border border-slate-300 px-3 py-2 text-slate-900 outline-none focus:border-slate-900 focus:ring-1 focus:ring-slate-900 aria-[invalid=true]:border-red-500"
                  />
                  {field.unit === '%' && <span className="text-slate-500">%</span>}
                  <span className="text-xs text-slate-500">
                    allowed {rangeText(saved.bounds[field.id], field.unit)}
                  </span>
                </div>
                {message !== undefined && (
                  <p role="alert" className="mt-1 text-sm text-red-600">
                    {message}
                  </p>
                )}
                <p id={`risk-${field.id}-hint`} className="mt-1 text-xs text-slate-500">
                  Now: {field.meaning(saved[field.id])}
                </p>
              </div>
            )
          })}

          <div className="flex flex-wrap items-center gap-3">
            <button
              type="submit"
              disabled={busy || !dirty}
              className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white transition hover:bg-slate-700 disabled:opacity-50"
            >
              Save limits
            </button>
            <button
              type="button"
              onClick={reset}
              disabled={busy || atDefaults}
              className="rounded-md border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-50"
            >
              Reset to defaults
            </button>
          </div>
          {error !== null && (
            <p role="alert" className="text-sm text-red-600">
              {error}
            </p>
          )}
          {notice !== null && <p className="text-sm text-green-700">{notice}</p>}
        </form>
      )}
    </section>
  )
}
