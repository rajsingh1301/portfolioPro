import type { ReactNode } from 'react'

/** A single narrow panel on the canvas: the name, the form, and the link to the other page. */
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
    <div className="flex min-h-dvh items-center justify-center bg-canvas px-4 py-10">
      <main className="w-full max-w-sm">
        <p className="mb-4 text-lg font-semibold tracking-tight">PortfolioPro</p>
        <div className="border border-rule bg-panel p-5">
          <h1 className="text-xl">{title}</h1>
          <p className="mt-1 text-sm text-ink-2">{subtitle}</p>
          {children}
        </div>
        <p className="mt-3 text-sm text-ink-2">{footer}</p>
      </main>
    </div>
  )
}
