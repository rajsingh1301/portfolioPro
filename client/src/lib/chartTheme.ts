import { ColorType, LineStyle } from 'lightweight-charts'
import type { DeepPartial, ChartOptions } from 'lightweight-charts'

/** The design tokens, read from CSS so a chart can never drift from the page. */
export function chartColors() {
  const style = getComputedStyle(document.documentElement)
  const read = (name: string, fallback: string) => style.getPropertyValue(name).trim() || fallback
  return {
    paper: read('--color-paper', '#f7f4ec'),
    ink: read('--color-ink', '#16140f'),
    ink2: read('--color-ink-2', '#4e493f'),
    ink3: read('--color-ink-3', '#66615a'),
    rule: read('--color-rule', '#d8d1c1'),
    gain: read('--color-gain', '#006300'),
    loss: read('--color-loss', '#b3261e'),
    series: [1, 2, 3].map((n) => read(`--color-series-${n}`, ['#2a78d6', '#eb6834', '#1baf7a'][n - 1])),
  }
}

export function withAlpha(hex: string, alpha: number): string {
  const value = hex.replace('#', '')
  const [r, g, b] = [0, 2, 4].map((i) => parseInt(value.slice(i, i + 2), 16))
  return `rgba(${r}, ${g}, ${b}, ${alpha})`
}

/** Options every chart in the app shares: recessive chrome, hairline horizontal grid, solid crosshair. */
export function baseChartOptions(colors: ReturnType<typeof chartColors>): DeepPartial<ChartOptions> {
  return {
    autoSize: true,
    layout: {
      background: { type: ColorType.Solid, color: colors.paper },
      textColor: colors.ink2,
      fontFamily: "'IBM Plex Sans', system-ui, sans-serif",
      fontSize: 12,
      attributionLogo: false,
    },
    // Horizontal hairlines only, solid and one step off the paper: recessive, never dashed.
    grid: { vertLines: { visible: false }, horzLines: { color: colors.rule } },
    rightPriceScale: { borderVisible: false },
    timeScale: { borderVisible: false },
    crosshair: {
      vertLine: { color: colors.ink3, width: 1, style: LineStyle.Solid, labelBackgroundColor: colors.ink },
      horzLine: { color: colors.ink3, width: 1, style: LineStyle.Solid, labelBackgroundColor: colors.ink },
    },
  }
}
