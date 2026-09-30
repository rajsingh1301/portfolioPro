import { ColorType, LineStyle } from 'lightweight-charts'
import type { ChartOptions, DeepPartial } from 'lightweight-charts'

/** The design tokens, read from CSS so a chart can never drift from the page or from its theme. */
export function chartColors() {
  const style = getComputedStyle(document.documentElement)
  const read = (name: string, fallback: string) => style.getPropertyValue(name).trim() || fallback
  return {
    canvas: read('--color-canvas', '#131722'),
    panel: read('--color-panel', '#1e222d'),
    ink: read('--color-ink', '#d1d4dc'),
    ink2: read('--color-ink-2', '#b2b5be'),
    ink3: read('--color-ink-3', '#9598a1'),
    rule: read('--color-rule', '#2a2e39'),
    edge: read('--color-edge', '#737987'),
    up: read('--color-up', '#26a69a'),
    down: read('--color-down', '#ef5350'),
    series: [1, 2, 3].map((n) => read(`--color-series-${n}`, ['#3987e5', '#d95926', '#199e70'][n - 1])),
  }
}

export type ChartColors = ReturnType<typeof chartColors>

export function withAlpha(color: string, alpha: number): string {
  const value = color.replace('#', '')
  const [r, g, b] = [0, 2, 4].map((i) => parseInt(value.slice(i, i + 2), 16))
  return `rgba(${r}, ${g}, ${b}, ${alpha})`
}

/**
 * Options every chart in the app shares: the panel's own background so it melts into it,
 * recessive horizontal hairlines, no frame, a solid crosshair. Also used to restyle a chart in
 * place when the theme changes.
 */
export function baseChartOptions(colors: ChartColors): DeepPartial<ChartOptions> {
  return {
    autoSize: true,
    layout: {
      background: { type: ColorType.Solid, color: colors.panel },
      textColor: colors.ink2,
      fontFamily: "'IBM Plex Sans', system-ui, sans-serif",
      fontSize: 11,
      attributionLogo: false,
      panes: { separatorColor: colors.rule, separatorHoverColor: colors.edge },
    },
    grid: { vertLines: { visible: false }, horzLines: { color: colors.rule } },
    rightPriceScale: { borderVisible: false },
    timeScale: { borderVisible: false },
    crosshair: {
      vertLine: { color: colors.ink3, width: 1, style: LineStyle.Solid, labelBackgroundColor: colors.edge },
      horzLine: { color: colors.ink3, width: 1, style: LineStyle.Solid, labelBackgroundColor: colors.edge },
    },
  }
}
