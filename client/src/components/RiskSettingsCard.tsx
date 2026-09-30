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

export function RiskSettingsCard({ className = '' }: { className?: string }) {
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
    <section aria-labelledby="risk-heading" className={`section ${className}`}>
      <h2 id="risk-heading" className="section-title">
        Risk limits
      </h2>
      <p className="mt-2 text-sm text-ink-2">
        Checked before every order. A rejected order is kept in your history with the reason.
      </p>

      {saved === null || form === null ? (
        <p role={error === null ? 'status' : 'alert'} className={error === null ? 'mt-4 text-sm text-ink-3' : 'notice-error mt-4'}>
          {error ?? 'Loading your limits…'}
        </p>
      ) : (
        <form onSubmit={handleSubmit} className="mt-2">
          {FIELDS.map((field) => {
            const message = fieldErrors[field.id]
            return (
              <div key={field.id} className="border-b border-rule py-4">
                <label htmlFor={`risk-${field.id}`} className="block text-sm font-medium">
                  {field.label}
                </label>
                <div className="mt-2 flex items-center gap-2">
                  {field.unit === '$' && <span className="text-ink-2">$</span>}
                  <input
                    id={`risk-${field.id}`}
                    inputMode="decimal"
                    value={form[field.id]}
                    onChange={(event) => setForm({ ...form, [field.id]: event.target.value })}
                    aria-invalid={message !== undefined}
                    aria-describedby={`risk-${field.id}-hint`}
                    className="input w-32 tabular-nums"
                  />
                  {field.unit === '%' && <span className="text-ink-2">%</span>}
                  <span className="text-xs text-ink-3">allowed {rangeText(saved.bounds[field.id], field.unit)}</span>
                </div>
                {message !== undefined && (
                  <p role="alert" className="notice-error mt-2">
                    {message}
                  </p>
                )}
                <p id={`risk-${field.id}-hint`} className="mt-2 text-xs text-ink-2">
                  Now: {field.meaning(saved[field.id])}
                </p>
              </div>
            )
          })}

          <div className="mt-5 flex flex-wrap items-center gap-3">
            <button type="submit" disabled={busy || !dirty} className="btn btn-primary">
              Save limits
            </button>
            <button type="button" onClick={reset} disabled={busy || atDefaults} className="btn">
              Reset to defaults
            </button>
          </div>
          {error !== null && (
            <p role="alert" className="notice-error mt-4">
              {error}
            </p>
          )}
          {notice !== null && (
            <p role="status" className="mt-4 border-l-2 border-gain py-1 pl-3 text-sm text-gain">
              {notice}
            </p>
          )}
        </form>
      )}
    </section>
  )
}
