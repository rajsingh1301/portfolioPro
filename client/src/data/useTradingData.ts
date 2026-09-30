import { useContext } from 'react'

import { TradingDataContext } from './tradingDataContext'
import type { TradingData } from './tradingDataContext'

export function useTradingData(): TradingData {
  const value = useContext(TradingDataContext)
  if (value === null) {
    throw new Error('useTradingData must be used inside a TradingDataProvider')
  }
  return value
}
