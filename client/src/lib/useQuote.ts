import { useCallback, useEffect, useRef, useState } from 'react'

import { errorMessage } from '../api/client'
import { fetchQuote } from '../api/trading'
import type { Quote } from '../types/trading'

/** Server quotes are cached for 15s, so a faster poll would only repeat the last answer. */
const POLL_MS = 15_000

type State = { symbol: string; quote: Quote | null; error: string | null }

/**
 * The live quote for one symbol, refreshed every 15s while the tab is showing. The result is
 * tagged with the symbol it was for, so switching symbol never shows the previous one's price
 * under the new one's name while the new answer is on its way. Only the latest request's answer
 * is applied, so a slow poll for the old symbol cannot overwrite the new one's price either.
 */
export function useQuote(symbol: string) {
  const [state, setState] = useState<State>({ symbol, quote: null, error: null })
  const latest = useRef(0)

  const settle = useCallback(
    (request: number, outcome: { quote: Quote } | { failure: unknown }) => {
      if (latest.current !== request) {
        return
      }
      if ('quote' in outcome) {
        setState({ symbol, quote: outcome.quote, error: null })
      } else {
        setState((previous) => ({
          symbol,
          // Keep the last good price for this symbol on screen, and note the failure beside it.
          quote: previous.symbol === symbol ? previous.quote : null,
          error: errorMessage(outcome.failure, `Could not load a price for ${symbol}`),
        }))
      }
    },
    [symbol],
  )

  const load = useCallback(() => {
    const request = ++latest.current
    return fetchQuote(symbol).then(
      (quote) => settle(request, { quote }),
      (failure: unknown) => settle(request, { failure }),
    )
  }, [symbol, settle])

  useEffect(() => {
    // Not `load()`: state is set only in the callbacks, never synchronously in the effect body.
    const request = ++latest.current
    fetchQuote(symbol).then(
      (quote) => settle(request, { quote }),
      (failure: unknown) => settle(request, { failure }),
    )
    const timer = window.setInterval(() => {
      if (document.visibilityState === 'visible') {
        void load()
      }
    }, POLL_MS)
    return () => window.clearInterval(timer)
  }, [symbol, load, settle])

  const current = state.symbol === symbol
  return {
    quote: current ? state.quote : null,
    error: current ? state.error : null,
    /** True until the first answer for this symbol arrives. */
    loading: !current || (state.quote === null && state.error === null),
    retry: () => void load(),
  }
}
