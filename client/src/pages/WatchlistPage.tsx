import { WatchlistTable } from '../components/workspace/WatchlistTable'

export function WatchlistPage() {
  return (
    <div className="h-full p-3">
      <h1 className="sr-only">Watchlist</h1>
      <div className="h-full border border-rule">
        <WatchlistTable variant="page" />
      </div>
    </div>
  )
}
