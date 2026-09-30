/**
 * Money arrives from the API as a decimal string. Intl needs a number to format, so
 * the conversion happens here and nowhere else, and only ever for display.
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

/** Like {@link formatUsd} but a gain carries an explicit plus, so direction never rests on colour. */
export function formatSignedUsd(amount: string): string {
  const formatted = formatUsd(amount)
  return Number(amount) > 0 ? `+${formatted}` : formatted
}

/** Text colour for a signed amount string: gain, loss, or plain ink for zero. */
export function pnlColor(amount: string | undefined): string {
  const parsed = Number(amount)
  if (amount === undefined || !Number.isFinite(parsed) || parsed === 0) {
    return 'text-ink-2'
  }
  return parsed > 0 ? 'text-gain' : 'text-loss'
}

/** A large amount in short form, e.g. $4.96T, for display only. */
export function formatCompactUsd(amount: string): string {
  const parsed = Number(amount)
  if (!Number.isFinite(parsed)) {
    return amount
  }
  return parsed.toLocaleString('en-US', {
    style: 'currency',
    currency: 'USD',
    notation: 'compact',
    maximumFractionDigits: 2,
  })
}

/**
 * quantity x price for the order ticket's estimate, in integer arithmetic so no float
 * ever touches a price (rule 1). Returns null unless the price is a plain decimal with
 * at most four places and the quantity a whole number.
 */
export function estimateValue(price: string, quantity: number): string | null {
  if (!/^\d+(\.\d{1,4})?$/.test(price) || !Number.isInteger(quantity) || quantity < 1) {
    return null
  }
  const [whole, fraction = ''] = price.split('.')
  const scaled = BigInt(whole + fraction.padEnd(4, '0')) * BigInt(quantity) // in 1/10000ths
  const cents = (scaled + 50n) / 100n // round half up to whole cents
  const dollars = cents / 100n
  const remainder = (cents % 100n).toString().padStart(2, '0')
  return formatUsd(`${dollars}.${remainder}`)
}
