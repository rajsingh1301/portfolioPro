/**
 * Money arrives from the API as a decimal string. Intl needs a number to format, so
 * the conversion happens here and nowhere else — and only ever for display.
 */
export function formatUsd(amount: string): string {
  const parsed = Number(amount)
  if (!Number.isFinite(parsed)) {
    return amount
  }
  return parsed.toLocaleString('en-US', {
    style: 'currency',
    currency: 'USD',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

/** Tailwind colour for a signed amount string: green for gains, red for losses, grey for zero. */
export function pnlColor(amount: string | undefined): string {
  const parsed = Number(amount)
  if (amount === undefined || !Number.isFinite(parsed) || parsed === 0) {
    return 'text-slate-700'
  }
  return parsed > 0 ? 'text-green-700' : 'text-red-600'
}
