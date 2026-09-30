import { useCallback, useEffect, useRef, useState } from 'react'

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
  // Every request takes a number. Only the answer to the most recently started one is used, so a
  // slow older response can never overwrite a newer one. Without this, a poll that began before the
  // user added a symbol could arrive after the reload that followed, and put the old list back.
  const latest = useRef(0)

  // The first load. Written out here, with setState only in the callbacks, rather than by
  // calling `reload`, so nothing sets state synchronously inside the effect.
  useEffect(() => {
    const request = ++latest.current
    fetcher()
      .then((data) => {
        if (latest.current === request) {
          setState({ data, error: null, loading: false })
        }
      })
      .catch((failure: unknown) => {
        if (latest.current === request) {
          setState({ data: null, error: errorMessage(failure, failureMessage), loading: false })
        }
      })
  }, [fetcher, failureMessage])

  /** A quiet refresh: keeps what is on screen, and swaps it or notes the failure beside it. */
  const reload = useCallback(async () => {
    const request = ++latest.current
    try {
      const data = await fetcher()
      if (latest.current === request) {
        setState({ data, error: null, loading: false })
      }
    } catch (failure) {
      if (latest.current === request) {
        setState((previous) => ({ data: previous.data, error: errorMessage(failure, failureMessage), loading: false }))
      }
    }
  }, [fetcher, failureMessage])

  /** The Retry button: shows the skeleton again, then loads. */
  const retry = useCallback(() => {
    setState((previous) => ({ data: previous.data, error: null, loading: previous.data === null }))
    void reload()
  }, [reload])

  return { state, reload, retry }
}
