import { useCallback, useEffect, useState } from 'react'

import { errorMessage } from '../api/client'
import { fetchQuote } from '../api/trading'
import type { Quote } from '../types/trading'

/** Server quotes are cached for 15s, so a faster poll would only repeat the last answer. */
const POLL_MS = 15_000

type State = { symbol: string; quote: Quote | null; error: string | null }

/**
 * The live quote for one symbol, refreshed every 15s while the tab is showing. The result is
 * tagged with the symbol it was for, so switching symbol never shows the previous one's price
 * under the new one's name while the new answer is on its way.
 */
export function useQuote(symbol: string) {
  const [state, setState] = useState<State>({ symbol, quote: null, error: null })

  const load = useCallback(
    () =>
      fetchQuote(symbol)
        .then((quote) => setState({ symbol, quote, error: null }))
        .catch((failure: unknown) =>
          setState((previous) => ({
            symbol,
            // Keep the last good price for this symbol on screen, and note the failure beside it.
            quote: previous.symbol === symbol ? previous.quote : null,
            error: errorMessage(failure, `Could not load a price for ${symbol}`),
          })),
        ),
    [symbol],
  )

  useEffect(() => {
    let active = true
    // Not `load()`: state is set only in the callbacks, and only while this effect is current.
    fetchQuote(symbol)
      .then((quote) => active && setState({ symbol, quote, error: null }))
      .catch(
        (failure: unknown) =>
          active &&
          setState((previous) => ({
            symbol,
            quote: previous.symbol === symbol ? previous.quote : null,
            error: errorMessage(failure, `Could not load a price for ${symbol}`),
          })),
      )
    const timer = window.setInterval(() => {
      if (document.visibilityState === 'visible') {
        void load()
      }
    }, POLL_MS)
    return () => {
      active = false
      window.clearInterval(timer)
    }
  }, [symbol, load])

  const current = state.symbol === symbol
  return {
    quote: current ? state.quote : null,
    error: current ? state.error : null,
    /** True until the first answer for this symbol arrives. */
    loading: !current || (state.quote === null && state.error === null),
    retry: () => void load(),
  }
}
