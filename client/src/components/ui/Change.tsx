import { arrow, formatSignedPercent, formatSignedUsd, pnlColor } from '../../lib/money'

/**
 * A signed change: an arrow, the amount and/or the percentage. The colour is the second signal;
 * the arrow and the sign are the first, so it reads the same in greyscale or with a colour deficiency.
 */
export function Change({
  amount,
  percent,
  className = '',
}: {
  amount?: string
  percent?: string
  className?: string
}) {
  const basis = amount ?? percent
  if (basis === undefined) {
    return <span className={`text-ink-3 ${className}`}>—</span>
  }
  const mark = arrow(basis)
  return (
    <span className={`whitespace-nowrap tabular-nums ${pnlColor(basis)} ${className}`}>
      {mark !== '' && (
        <span aria-hidden className="mr-1 text-[0.7em]">
          {mark}
        </span>
      )}
      {amount !== undefined && formatSignedUsd(amount)}
      {amount !== undefined && percent !== undefined && ' '}
      {percent !== undefined && (amount !== undefined ? `(${formatSignedPercent(percent)})` : formatSignedPercent(percent))}
    </span>
  )
}
