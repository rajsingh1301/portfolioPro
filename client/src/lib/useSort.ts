import { useMemo, useState } from 'react'

export type SortDirection = 'asc' | 'desc'

type Value = number | string | null | undefined

/**
 * Sorting for a table. Click a column to sort by it, click again to reverse. Text sorts
 * alphabetically and numbers numerically; a row with no value for the column goes last either way.
 */
export function useSort<Row, Key extends string>(
  rows: Row[],
  accessors: Record<Key, (row: Row) => Value>,
  initial: { key: Key; direction: SortDirection },
) {
  const [sort, setSort] = useState(initial)

  const sorted = useMemo(() => {
    const read = accessors[sort.key]
    const factor = sort.direction === 'asc' ? 1 : -1
    return [...rows].sort((a, b) => {
      const left = read(a)
      const right = read(b)
      const leftMissing = left === null || left === undefined || left === ''
      const rightMissing = right === null || right === undefined || right === ''
      if (leftMissing || rightMissing) {
        return leftMissing === rightMissing ? 0 : leftMissing ? 1 : -1
      }
      if (typeof left === 'number' && typeof right === 'number') {
        return (left - right) * factor
      }
      return String(left).localeCompare(String(right)) * factor
    })
  }, [rows, accessors, sort])

  return {
    sorted,
    sort,
    /** Sort by a column; the same column again reverses it. Numbers start high-to-low, text A-to-Z. */
    toggle: (key: Key, startsAscending = false) =>
      setSort((current) =>
        current.key === key
          ? { key, direction: current.direction === 'asc' ? 'desc' : 'asc' }
          : { key, direction: startsAscending ? 'asc' : 'desc' },
      ),
    ariaSort: (key: Key): 'ascending' | 'descending' | 'none' =>
      sort.key !== key ? 'none' : sort.direction === 'asc' ? 'ascending' : 'descending',
  }
}
