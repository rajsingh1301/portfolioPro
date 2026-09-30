import { RiskSettingsCard } from '../components/RiskSettingsCard'

export function RiskPage() {
  return (
    <div className="h-full overflow-y-auto p-3">
      <h1 className="sr-only">Risk limits</h1>
      <div className="mx-auto max-w-lg border border-rule">
        <RiskSettingsCard />
      </div>
    </div>
  )
}
