// Accessibility audit of the running app: axe-core (WCAG 2.0/2.1/2.2 A and AA, plus its
// best-practice rules) on the login page, the signup page and a populated dashboard, at a
// desktop and a phone width, and then a measurement of every tap target on the phone.
// Exits non-zero on the first thing wrong, so it can gate a change. `npm run a11y`.
import { chromium } from 'playwright'
import fs from 'node:fs'
import { createRequire } from 'node:module'
const axeSource = fs.readFileSync(createRequire(import.meta.url).resolve('axe-core/axe.min.js'), 'utf8')
const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:5173'
let failures = 0
const b = await chromium.launch()
async function audit(p, label) {
  await p.evaluate(axeSource)
  const result = await p.evaluate(async () => {
    const r = await window.axe.run(document, { runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa', 'best-practice'] } })
    return { violations: r.violations.map(v => ({ id: v.id, impact: v.impact, help: v.help, nodes: v.nodes.slice(0, 4).map(n => n.target.join(' ') + ' :: ' + (n.failureSummary || '').split('\n')[1]) })), passes: r.passes.length, incomplete: r.incomplete.map(i => i.id) }
  })
  failures += result.violations.length
  console.log(`\n== ${label}: ${result.violations.length} violation(s), ${result.passes} rules passed, incomplete: [${result.incomplete.join(', ')}]`)
  for (const v of result.violations) { console.log(`  [${v.impact}] ${v.id}: ${v.help}`); v.nodes.forEach(n => console.log('     -', n.slice(0, 200))) }
}
for (const [w, h, tag] of [[1280, 900, 'desktop'], [390, 844, 'mobile']]) {
  const ctx = await b.newContext({ viewport: { width: w, height: h }, isMobile: w < 600, hasTouch: w < 600 })
  const p = await ctx.newPage()
  await p.goto(`${BASE}/login`); await p.waitForSelector('h1'); await audit(p, `login (${tag})`)
  await p.goto(`${BASE}/signup`); await p.waitForSelector('h1'); await audit(p, `signup (${tag})`)
  await p.fill('input[name=email]', `axe${Date.now()}${tag}@example.com`)
  for (const el of await p.locator('input[type=password]').all()) await el.fill('Password123')
  await p.click('button[type=submit]'); await p.waitForURL(`${BASE}/`)
  await p.fill('input[aria-label="Search stocks"]', 'aapl'); await p.click('button:has-text("Search")')
  await p.waitForSelector('ul button:not([disabled])'); await p.locator('ul button').first().click()
  await p.waitForSelector('dl >> text=Market cap', { timeout: 20000 }); await p.waitForSelector('canvas')
  await p.click('button:text-is("Watch")'); await p.waitForSelector('button:text-is("Unwatch")')
  await p.fill('#quantity', '2'); await p.locator('button[type=submit]', { hasText: /^Place market order$/ }).click()
  await p.waitForSelector('td:has-text("filled")')
  for (const l of ['SMA 20', 'RSI', 'MACD']) await p.locator('[aria-label=Indicators] button', { hasText: new RegExp(`^\\s*${l}\\s*$`) }).click()
  await p.waitForSelector('text=Indicators describe past prices', { timeout: 25000 })
  await p.locator('summary').first().click().catch(() => {})
  await p.waitForTimeout(800)
  await audit(p, `dashboard, populated (${tag})`)
  await ctx.close()
}

// ---- tap targets on a phone: every button, input, chip and summary is at least 44px tall
{
  const ctx = await b.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true })
  const p = await ctx.newPage()
  await p.goto(`${BASE}/signup`)
  await p.fill('input[name=email]', `tap${Date.now()}@example.com`)
  for (const el of await p.locator('input[type=password]').all()) await el.fill('Password123')
  await p.click('button[type=submit]'); await p.waitForURL(`${BASE}/`)
  await p.fill('input[aria-label="Search stocks"]', 'aapl'); await p.click('button:has-text("Search")')
  await p.waitForSelector('ul button:not([disabled])'); await p.locator('ul button').first().click()
  await p.waitForSelector('dl >> text=Market cap', { timeout: 20000 }); await p.waitForSelector('canvas')
  await p.click('button:text-is("Watch")'); await p.waitForSelector('button:text-is("Unwatch")')
  await p.locator('button[aria-pressed]', { hasText: 'Limit' }).click()
  await p.fill('#quantity', '1'); await p.fill('#price', '100')
  await p.locator('button[type=submit]', { hasText: /^Place limit order$/ }).click(); await p.waitForSelector('td:has-text("pending")')
  const small = await p.evaluate(() => {
    const out = []
    for (const el of document.querySelectorAll('button, input:not([type=hidden]), select, textarea, summary, a[href], [role=button]')) {
      const r = el.getBoundingClientRect(); const cs = getComputedStyle(el)
      if (r.width === 0 || r.height === 0 || cs.visibility === 'hidden' || el.closest('.sr-only') || el.classList.contains('skip-link')) continue
      // A link inside a sentence, and a checkbox whose whole label row is the target, are exempt.
      if ((el.tagName === 'A' && cs.display === 'inline') || el.type === 'checkbox') continue
      if (r.height < 43.5) out.push(`${el.tagName.toLowerCase()} "${(el.getAttribute('aria-label') || el.textContent || el.id).trim().slice(0, 28)}" ${Math.round(r.width)}x${Math.round(r.height)}`)
    }
    return out
  })
  failures += small.length
  console.log(small.length ? `\n== tap targets: ${small.length} under 44px\n  - ` + small.join('\n  - ') : '\n== tap targets: all >= 44px tall on a phone')
  await ctx.close()
}

await b.close()
if (failures > 0) {
  console.error(`\nA11Y FAILED: ${failures} problem(s)`)
  process.exit(1)
}
console.log('\nA11Y PASSED')
