// End-to-end journey through the whole app in a real browser: sign up, look up a stock,
// read its chart and indicators, follow it, trade at market and with limit and stop-loss
// orders, hit and reset a risk limit, reload, log out and back in.
//
// It uses the live Finnhub and Twelve Data keys, creates a brand-new account each run and
// stops at the first assertion that fails. Run it with `npm run journey` from this folder.
import { chromium } from 'playwright'
import assert from 'node:assert/strict'

// Drives the running app, so start the backend and the client first (see the README).
const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:5173'
const email = `journey${Date.now()}@example.com`
const password = 'Password123'
const b = await chromium.launch()
const p = await b.newPage({ viewport: { width: 1200, height: 2600 } })
const problems = []
p.on('pageerror', e => problems.push('PAGEERROR ' + e.message.slice(0, 200)))
p.on('console', m => { if (m.type() === 'error' && !/status of (400|422)/.test(m.text())) problems.push('CONSOLE ' + m.text().slice(0, 200)) })
let step = 0
const ok = (msg) => console.log(`✓ ${++step}. ${msg}`)
const section = (h) => p.locator(`section:has(h2:text-is("${h}"))`)
const money = (t) => Number(t.replace(/[^0-9.\-]/g, ''))
// The figures are a definition list, so a stat is found by its label, not by its styling.
const stat = async (label) => money(await p.locator(`dt:text-is("${label}") + dd`).first().innerText())

// 1. signup lands on the dashboard with the starting balance
await p.goto(`${BASE}/signup`)
await p.fill('input[name=email]', email)
for (const el of await p.locator('input[type=password]').all()) await el.fill(password)
await p.click('button[type=submit]')
await p.waitForURL(`${BASE}/`)
await p.locator('dt:text-is("Total value") + dd:has-text("$")').waitFor()
assert.equal(await stat('Total value'), 100000); assert.equal(await stat('Cash'), 100000)
ok('signup -> dashboard with $100,000 cash and nothing held')

// 2. search, quote, fundamentals, chart
async function pick(query, symbol) {
  await p.fill('input[aria-label="Search stocks"]', query); await p.click('button:has-text("Search")')
  await p.waitForSelector(`ul button:has-text("${symbol}"):not([disabled])`)
  await p.locator(`ul button:has-text("${symbol}")`).first().click()
  await p.waitForSelector(`text=${symbol} last price`)
}
await pick('aapl', 'AAPL')
await p.waitForSelector('dl >> text=Market cap', { timeout: 20000 }); await p.waitForSelector('canvas')
const price = money(await p.locator('[data-testid=quote-price]').innerText())
assert.ok(price > 10 && price < 5000, `implausible price ${price}`)
assert.match(await p.locator('dl:has(dt:text-is("Market cap"))').innerText(), /Market cap[\s\S]*P\/E/i)
ok(`search -> AAPL quote $${price}, fundamentals and candle chart`)

// 3. indicators and their readings
await p.locator('[aria-label=Indicators] button', { hasText: /^SMA 20$/ }).click()
await p.locator('[aria-label=Indicators] button', { hasText: /^RSI$/ }).click()
await p.waitForSelector('text=Indicators describe past prices', { timeout: 25000 })
const readings = await p.locator('[aria-label="Indicator readings"] li').allInnerTexts()
assert.ok(readings.length >= 3, 'expected readings'); assert.ok(readings.some(r => /RSI is/.test(r)))
for (const r of readings) assert.doesNotMatch(r, /\b(buy|sell|bullish|bearish|should|recommend)\b/i)
ok(`indicators -> ${readings.length} neutral readings, none advisory`)

// 4. watchlist
await p.click('button:text-is("Watch")'); await p.waitForSelector('button:text-is("Unwatch")')
await section('Watchlist').locator('li').first().waitFor()
assert.match(await section('Watchlist').innerText(), /AAPL/)
ok('watch -> AAPL on the watchlist with a price and change')

// 5. market buy updates cash, holdings, allocation, order history
await p.fill('#quantity', '3')
await p.locator('button[type=submit]', { hasText: /^Place market order$/ }).click()
await p.waitForSelector('td:has-text("filled")')
await p.locator('dt:text-is("Invested") + dd:has-text("$"):not(:has-text("…"))').waitFor()
await p.waitForTimeout(800)
const cashAfter = await stat('Cash'), investedAfter = await stat('Invested'), total = await stat('Total value')
assert.ok(Math.abs(cashAfter - (100000 - 3 * price)) < 1, `cash ${cashAfter}`)
assert.ok(Math.abs(investedAfter - 3 * price) < 1); assert.ok(Math.abs(total - 100000) < 5)
assert.match(await section('Holdings').innerText(), /AAPL\s+3/)
assert.match(await section('Allocation').innerText(), /AAPL[\s\S]*Cash/)
ok(`market buy 3 -> cash $${cashAfter}, invested $${investedAfter}, total value ~unchanged`)

// 6. limit order far below market waits, then cancels
await p.locator('[aria-label="Order type"] button', { hasText: /^Limit$/ }).click()
await p.fill('#quantity', '1'); await p.fill('#price', '50.00')
await p.locator('button[type=submit]', { hasText: /^Place limit order$/ }).click()
await p.waitForSelector('td:has-text("pending")')
assert.equal(await stat('Cash'), cashAfter)
await p.locator('button:text-is("Cancel")').first().click()
await p.waitForSelector('td:has-text("cancelled")')
ok('limit @ $50 -> pending with no cash moved, then cancelled')

// 7. limit just above market fills by itself (real scheduler, no reload). A buy limit is
// risk-checked at its limit price, so it must stay inside the order-value limit.
const limit = (Math.ceil(price * 1.05)).toFixed(2)
await p.fill('#price', limit)
await p.locator('button[type=submit]', { hasText: /^Place limit order$/ }).click()
await p.waitForSelector('tr:has-text("Limit"):has-text("pending")')
await p.waitForSelector('tr:has-text("Limit"):has-text("filled")', { timeout: 45000 })
ok(`limit @ $${limit} (above the $${price} market) -> filled by the scheduler, with no page reload`)

// 8. market buy with an attached stop-loss
await p.locator('[aria-label="Order type"] button', { hasText: /^Market$/ }).click()
await p.locator('label:has-text("stop-loss") input').check()
await p.locator('button[type=submit]', { hasText: /^Place market order$/ }).click()
await p.waitForSelector('tr:has-text("Stop-loss"):has-text("pending")', { timeout: 20000 })
const stopText = await p.locator('tr:has-text("Stop-loss")').first().innerText()
const stopPrice = money(stopText.match(/stop \$([\d,.]+)/)[1])
assert.ok(Math.abs(stopPrice - price * 0.95) < price * 0.02, `stop ${stopPrice} vs ${price * 0.95}`)
ok(`buy + attached stop-loss -> pending stop at $${stopPrice} (~5% below the fill)`)

// 9. risk limit governs the next order, and resets
const risk = section('Risk limits')
await risk.locator('#risk-maxOrderValue').fill('100'); await risk.locator('button:has-text("Save limits")').click()
await risk.locator('text=Saved.').waitFor()
await p.locator('button[type=submit]', { hasText: /^Place market order$/ }).click()
await p.waitForSelector('text=Order rejected: order too large')
await p.waitForSelector('td:has-text("Rejected: order too large")')
await risk.locator('button:has-text("Reset to defaults")').click(); await risk.locator('text=Saved.').waitFor()
assert.equal(await risk.locator('#risk-maxOrderValue').inputValue(), '5000.00')
ok('risk limit $100 -> next order rejected and recorded; reset restores $5,000')

// 10. everything survives a reload
const before = { cash: await stat('Cash'), orders: await section('Orders').locator('tbody tr').count() }
await p.reload(); await section('Holdings').locator('tbody tr').first().waitFor(); await p.waitForTimeout(800)
assert.equal(await stat('Cash'), before.cash); assert.equal(await section('Orders').locator('tbody tr').count(), before.orders)
assert.match(await section('Watchlist').innerText(), /AAPL/)
ok(`reload -> cash, ${before.orders} orders, holdings and watchlist all still there`)

// 11. log out, log back in with the address typed differently
await p.click('button:has-text("Log out")'); await p.waitForURL(`${BASE}/login`)
await p.fill('input[name=email]', `  ${email.toUpperCase()}  `); await p.fill('input[type=password]', password)
await p.click('button[type=submit]'); await p.waitForURL(`${BASE}/`)
await section('Holdings').locator('tbody tr').first().waitFor()
assert.equal(await stat('Cash'), before.cash)
ok('log out -> log in with a padded, upper-case email -> same account, same data')

// 12. protected routes really are protected
await p.evaluate(() => localStorage.clear()); await p.goto(`${BASE}/`); await p.waitForURL(`${BASE}/login`)
ok('no token -> redirected to login')

assert.deepEqual(problems, [], 'unexpected browser errors: ' + problems.join(' | '))
ok('no unexpected console or page errors in the whole run')
await p.screenshot({ path: '/tmp/claude-501/pw/journey-end.png' })
await b.close()
console.log(`\nJOURNEY PASSED (${step} checks) as ${email}`)
