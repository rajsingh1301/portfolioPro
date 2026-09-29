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
