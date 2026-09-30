// Accessibility audit of the running app: axe-core (WCAG 2.0, 2.1 and 2.2 A and AA, plus its
// best-practice rules) on the login and signup pages and on every signed-in page, each in the dark
// and the light theme at a desktop and a phone width, with the command palette open as well. Then it
// measures every tap target on a phone. Exits non-zero on the first thing wrong, so it can gate a
// change. `npm run a11y`.
//
// It signs up a fresh account and places one order so the tables have rows. To audit an account
// that already has history (a performance curve, a donut with several holdings), pass
// A11Y_EMAIL and A11Y_PASSWORD.
import { chromium } from 'playwright'
import fs from 'node:fs'
import { createRequire } from 'node:module'

const axeSource = fs.readFileSync(createRequire(import.meta.url).resolve('axe-core/axe.min.js'), 'utf8')
const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:5173'
const browser = await chromium.launch()
let failures = 0

async function audit(page, label) {
  await page.evaluate(axeSource)
  const result = await page.evaluate(async () => {
    const report = await window.axe.run(document, {
      runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa', 'best-practice'] },
    })
    return {
      violations: report.violations.map((v) => ({
        id: v.id,
        impact: v.impact,
        help: v.help,
        nodes: v.nodes.slice(0, 4).map((n) => `${n.target.join(' ')} :: ${(n.failureSummary || '').split('\n')[1] ?? ''}`),
      })),
      passes: report.passes.length,
    }
  })
  failures += result.violations.length
  console.log(`${result.violations.length === 0 ? '✓' : '✗'} ${label}: ${result.violations.length} violation(s), ${result.passes} rules passed`)
  for (const v of result.violations) {
    console.log(`    [${v.impact}] ${v.id}: ${v.help}`)
    v.nodes.forEach((n) => console.log('       -', n.slice(0, 220)))
  }
}

async function signedInContext(viewport, theme, credentials) {
  const context = await browser.newContext({ viewport, isMobile: viewport.width < 600, hasTouch: viewport.width < 600 })
  await context.addInitScript((value) => {
    try {
      localStorage.setItem('pp-theme', value)
    } catch {
      // Storage blocked: the default (dark) applies.
    }
  }, theme)
  const page = await context.newPage()
  await page.goto(`${BASE}/${credentials.exists ? 'login' : 'signup'}`, { waitUntil: 'domcontentloaded' })
  await page.fill('input[name=email]', credentials.email)
  for (const field of await page.locator('input[type=password]').all()) await field.fill(credentials.password)
  await page.click('button[type=submit]')
  await page.waitForURL(/localhost:\d+\/(\?.*)?$/)
  return { context, page }
}

const credentials = process.env.A11Y_EMAIL
  ? { email: process.env.A11Y_EMAIL, password: process.env.A11Y_PASSWORD ?? 'Password123', exists: true }
  : { email: `a11y${Date.now()}@example.com`, password: 'Password123', exists: false }

// ---- the two public pages, in both themes and at both widths
for (const theme of ['dark', 'light']) {
  for (const [width, height, size] of [[1440, 900, 'desktop'], [390, 844, 'phone']]) {
    const context = await browser.newContext({ viewport: { width, height }, isMobile: width < 600, hasTouch: width < 600 })
    await context.addInitScript((value) => localStorage.setItem('pp-theme', value), theme)
    const page = await context.newPage()
    for (const path of ['login', 'signup']) {
      await page.goto(`${BASE}/${path}`, { waitUntil: 'domcontentloaded' })
      await page.waitForSelector('h1')
      await audit(page, `${path} (${theme}, ${size})`)
    }
    await context.close()
  }
}

// ---- every signed-in page, in both themes and at both widths
let first = true
for (const theme of ['dark', 'light']) {
  for (const [width, height, size] of [[1440, 900, 'desktop'], [390, 844, 'phone']]) {
    const { context, page } = await signedInContext({ width, height }, theme, first ? credentials : { ...credentials, exists: true })
    first = false
    await page.locator('section[aria-label$="chart"] canvas').first().waitFor({ timeout: 25000 })

    if (theme === 'dark' && size === 'desktop' && !process.env.A11Y_EMAIL) {
      // Give the tables a row to render: one market buy of the symbol on screen.
      await page.locator('#quantity').fill('1')
      await page.locator('section[aria-labelledby="ticket-heading"] button[type=submit]').click()
      await page.locator('[role=status]:has-text("filled")').waitFor()
    }
    await page.waitForTimeout(1200)

    await audit(page, `dashboard (${theme}, ${size})`)
    if (size === 'desktop') {
      await page.locator('summary:has-text("Indicators")').click()
      await page.locator('label:has-text("SMA 20") input').check()
      await page.locator('label:has-text("RSI 14") input').check()
      await page.locator('[aria-label="Indicator readings"] li').first().waitFor({ timeout: 25000 })
      await audit(page, `dashboard with indicators and their readings (${theme}, ${size})`)
      await page.locator('summary:has-text("Indicators")').click()
    }

    await page.keyboard.press('Control+k')
    await page.locator('dialog[open] input[role=combobox]').fill('a')
    await page.waitForTimeout(700)
    await audit(page, `command palette open (${theme}, ${size})`)
    await page.keyboard.press('Escape')

    for (const path of ['charts', 'portfolio', 'orders', 'watchlist', 'risk']) {
      await page.goto(`${BASE}/${path}`, { waitUntil: 'domcontentloaded' })
      // The page itself, not the "checking your session" placeholder that shows first.
      await page.waitForSelector('main h1', { state: 'attached' })
      await page.waitForTimeout(1500)
      await audit(page, `${path} (${theme}, ${size})`)
    }
    await context.close()
  }
}

// ---- every focusable control shows a visible focus ring, in both themes
for (const theme of ['dark', 'light']) {
  const { context, page } = await signedInContext({ width: 1440, height: 900 }, theme, { ...credentials, exists: true })
  await page.locator('section[aria-label$="chart"] canvas').first().waitFor({ timeout: 25000 })
  const unringed = []
  for (let i = 0; i < 40; i++) {
    await page.keyboard.press('Tab')
    await page.waitForTimeout(40)
    const state = await page.evaluate(() => {
      const el = document.activeElement
      if (!(el instanceof HTMLElement) || el === document.body) return null
      const s = getComputedStyle(el)
      const label = el.getAttribute('aria-label') || el.textContent?.trim().slice(0, 24) || el.tagName
      return { label, visible: s.outlineStyle !== 'none' && parseFloat(s.outlineWidth) >= 2, hidden: el.getBoundingClientRect().width === 0 }
    })
    if (state !== null && !state.visible && !state.hidden) unringed.push(state.label)
  }
  failures += unringed.length
  console.log(`${unringed.length === 0 ? '✓' : '✗'} keyboard focus ring (${theme}): ${unringed.length === 0 ? '40 Tab stops, every one shows a 2px ring' : 'no ring on ' + unringed.join(', ')}`)
  await context.close()
}

// ---- tap targets on a phone: every button, input, tab and summary is at least 44px tall
{
  const { context, page } = await signedInContext({ width: 390, height: 844 }, 'dark', { ...credentials, exists: true })
  await page.locator('section[aria-label$="chart"] canvas').first().waitFor({ timeout: 25000 })
  const measure = () =>
    page.evaluate(() => {
      const out = []
      for (const el of document.querySelectorAll('button, input:not([type=hidden]), select, textarea, summary, a[href], [role=tab], [role=option]')) {
        const r = el.getBoundingClientRect()
        const s = getComputedStyle(el)
        if (r.width === 0 || r.height === 0 || s.visibility === 'hidden' || el.closest('.sr-only') || el.classList.contains('skip-link')) continue
        // A checkbox's whole label row is its target; a link inside a sentence is exempt.
        if (el.type === 'checkbox' || (el.tagName === 'A' && s.display === 'inline')) continue
        if (r.height < 43.5) {
          out.push(`${el.tagName.toLowerCase()} "${(el.getAttribute('aria-label') || el.textContent || el.id).trim().slice(0, 26)}" ${Math.round(r.width)}x${Math.round(r.height)}`)
        }
      }
      return out
    })
  const small = []
  for (const tab of ['Chart', 'Watchlist', 'Order', 'Positions']) {
    await page.locator(`[role=tablist][aria-label="Workspace"] [role=tab]:text-is("${tab}")`).click()
    await page.waitForTimeout(700)
    small.push(...(await measure()).map((item) => `${tab}: ${item}`))
  }
  for (const path of ['portfolio', 'orders', 'watchlist', 'risk']) {
    await page.goto(`${BASE}/${path}`, { waitUntil: 'domcontentloaded' })
    await page.waitForSelector('main h1', { state: 'attached' })
    await page.waitForTimeout(1200)
    small.push(...(await measure()).map((item) => `${path}: ${item}`))
  }
  failures += small.length
  console.log(small.length ? `✗ tap targets: ${small.length} under 44px\n  - ${[...new Set(small)].join('\n  - ')}` : '✓ tap targets: every control on every page is at least 44px tall on a phone')
  await context.close()
}

await browser.close()
if (failures > 0) {
  console.error(`\nA11Y FAILED: ${failures} problem(s)`)
  process.exit(1)
}
console.log('\nA11Y PASSED')
