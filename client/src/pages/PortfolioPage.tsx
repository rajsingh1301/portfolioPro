import { AllocationDonut } from '../components/portfolio/AllocationDonut'
import { HoldingsTable } from '../components/portfolio/HoldingsTable'
import { PerformancePanels } from '../components/portfolio/PerformancePanel'
import { SummaryStrip } from '../components/portfolio/SummaryStrip'

/** The whole portfolio on one page: the five numbers, the holdings, where the money sits, and how it has moved. */
export function PortfolioPage() {
  return (
    <div className="h-full overflow-y-auto">
      <div className="mx-auto flex max-w-[110rem] flex-col gap-3 p-3">
        <h1 className="sr-only">Portfolio</h1>
        <SummaryStrip />

        <div className="grid items-start gap-3 xl:grid-cols-[minmax(0,1fr)_26rem]">
          <section aria-labelledby="holdings-heading" className="panel border border-rule">
            <div className="panel-header">
              <h2 id="holdings-heading">Holdings</h2>
            </div>
            <div className="overflow-auto">
              <HoldingsTable />
            </div>
          </section>
          <section aria-labelledby="allocation-heading" className="panel border border-rule">
            <div className="panel-header">
              <h2 id="allocation-heading">Allocation</h2>
            </div>
            <AllocationDonut />
          </section>
        </div>

        <div className="grid items-start gap-3 [&>section]:border [&>section]:border-rule xl:grid-cols-[minmax(0,1fr)_26rem]">
          <PerformancePanels />
        </div>
      </div>
    </div>
  )
}
