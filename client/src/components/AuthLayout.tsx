import type { ReactNode } from 'react'

const PRINCIPLES = [
  'Market, limit and stop-loss orders',
  'Risk limits you set, and can change',
  'Indicators that describe a price, never tell you what to do',
]

/**
 * Two unequal halves on a wide screen: what this is on the left, the form on the right.
 * On a phone the form comes straight after a short introduction.
 */
export function AuthLayout({
  title,
  subtitle,
  children,
  footer,
}: {
  title: string
  subtitle: string
  children: ReactNode
  footer: ReactNode
}) {
  return (
    <div className="min-h-dvh lg:grid lg:grid-cols-[minmax(0,5fr)_minmax(0,4fr)]">
      <aside className="border-b border-rule px-4 py-8 sm:px-10 lg:flex lg:flex-col lg:justify-between lg:border-b-0 lg:border-r lg:px-16 lg:py-14">
        <p className="font-display text-xl font-semibold tracking-tight">PortfolioPro</p>
        <div className="mt-8 max-w-xl lg:mt-0">
          <p className="font-display text-display font-medium tracking-tight">
            Practise trading against real prices.
          </p>
          <p className="mt-4 max-w-md text-base text-ink-2">
            Every account starts with $100,000 of paper money. Quotes, charts and fundamentals are live
            US market data; nothing you do here touches a real account.
          </p>
        </div>
        <ul className="mt-10 hidden max-w-md border-t border-ink text-sm text-ink-2 sm:block lg:mt-0">
          {PRINCIPLES.map((line) => (
            <li key={line} className="border-b border-rule py-3">
              {line}
            </li>
          ))}
        </ul>
      </aside>

      <main className="flex items-center px-4 py-10 sm:px-10 lg:px-16">
        <div className="w-full max-w-sm">
          <h1 className="text-2xl">{title}</h1>
          <p className="mt-2 text-base text-ink-2">{subtitle}</p>
          {children}
          <p className="mt-8 border-t border-rule pt-5 text-sm text-ink-2">{footer}</p>
        </div>
      </main>
    </div>
  )
}
