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

const cents = (amount: string) => Math.round(Number(amount) * 100)

/**
 * The signed change between two money strings and the percentage it is of the first, worked in
 * whole cents so no float error creeps in. For display: nothing is stored or sent back.
 */
export function changeBetween(from: string, to: string): { amount: string; percent: string } {
  const difference = cents(to) - cents(from)
  const base = cents(from)
  return {
    amount: (difference / 100).toFixed(2),
    percent: base === 0 ? '0.00' : ((difference / base) * 100).toFixed(2),
  }
}

/** A whole-number count in short form, e.g. 1.2M, for volumes. */
export function formatCompactNumber(value: number): string {
  return value.toLocaleString('en-US', { notation: 'compact', maximumFractionDigits: 2 })
}

export type Direction = 'up' | 'down' | 'flat'

/** Which way a signed amount string points. Anything that is not a number, or is zero, is flat. */
export function direction(amount: string | undefined): Direction {
  const parsed = Number(amount)
  if (amount === undefined || !Number.isFinite(parsed) || parsed === 0) {
    return 'flat'
  }
  return parsed > 0 ? 'up' : 'down'
}

/** Text colour for a signed amount string: up, down, or plain secondary ink for zero. */
export function pnlColor(amount: string | undefined): string {
  const way = direction(amount)
  return way === 'up' ? 'text-up' : way === 'down' ? 'text-down' : 'text-ink-2'
}

/** The arrow that says the same thing as the colour, so direction never rests on colour alone. */
export function arrow(amount: string | undefined): string {
  const way = direction(amount)
  return way === 'up' ? '▲' : way === 'down' ? '▼' : ''
}

/** A percentage string with an explicit plus for a gain, e.g. "+1.25%". */
export function formatSignedPercent(percent: string | undefined): string {
  if (percent === undefined) {
    return '—'
  }
  return Number(percent) > 0 ? `+${percent}%` : `${percent}%`
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
