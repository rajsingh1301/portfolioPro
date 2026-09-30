import { useCallback, useEffect, useState } from 'react'

import { errorMessage } from '../api/client'

/**
 * One piece of server data with its own loading and error state, so a failing endpoint shows
 * a retry button in its own place instead of blanking the page. A reload that fails keeps the
 * data already on screen and reports the error beside it.
 */
export interface Resource<T> {
  data: T | null
  error: string | null
  /** True only until the first answer arrives: a refresh never puts a skeleton over data. */
  loading: boolean
}

export function useResource<T>(fetcher: () => Promise<T>, failureMessage: string) {
  const [state, setState] = useState<Resource<T>>({ data: null, error: null, loading: true })

  // The first load. Written out here, with setState only in the callbacks, rather than by
  // calling `reload`, so nothing sets state synchronously inside the effect.
  useEffect(() => {
    let active = true
    fetcher()
      .then((data) => {
        if (active) {
          setState({ data, error: null, loading: false })
        }
      })
      .catch((failure: unknown) => {
        if (active) {
          setState({ data: null, error: errorMessage(failure, failureMessage), loading: false })
        }
      })
    return () => {
      active = false
    }
  }, [fetcher, failureMessage])

  /** A quiet refresh: keeps what is on screen, and swaps it or notes the failure beside it. */
  const reload = useCallback(async () => {
    try {
      const data = await fetcher()
      setState({ data, error: null, loading: false })
    } catch (failure) {
      setState((previous) => ({ data: previous.data, error: errorMessage(failure, failureMessage), loading: false }))
    }
  }, [fetcher, failureMessage])

  /** The Retry button: shows the skeleton again, then loads. */
  const retry = useCallback(() => {
    setState((previous) => ({ data: previous.data, error: null, loading: previous.data === null }))
    void reload()
  }, [reload])

  return { state, reload, retry }
}
