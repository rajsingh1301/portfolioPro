import { ChartPanel } from '../components/workspace/ChartPanel'
import { useQuote } from '../lib/useQuote'
import { useSelectedSymbol } from '../lib/useSelectedSymbol'

/** The chart on its own, full size, with the company's fundamentals above it and the data table below. */
export function ChartsPage() {
  const { symbol } = useSelectedSymbol()
  const quote = useQuote(symbol)
  return (
    <div className="h-full">
      <h1 className="sr-only">Charts</h1>
      <ChartPanel symbol={symbol} quote={quote} expanded />
    </div>
  )
}
