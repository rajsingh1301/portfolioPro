// End-to-end journey through the whole app in a real browser: sign up, find a symbol with the
// command palette, read its chart and indicators, follow it, trade with the keyboard and with
// limit and stop-loss orders, hit and reset a risk limit, look at the portfolio, orders and
// watchlist pages, switch theme, resize and collapse panels, recover from a failing endpoint,
// log out and back in, and use it on a phone.
//
// It uses the live Finnhub and Twelve Data keys, creates a brand-new account each run and stops at
// the first assertion that fails. Start the backend and the client first (see the README), then
// run it with `npm run journey` from this folder.
import { chromium } from 'playwright'
import assert from 'node:assert/strict'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:5173'
const email = `journey${Date.now()}@example.com`
const password = 'Password123'
const browser = await chromium.launch()
const problems = []
let allowServerErrors = false
let step = 0
const ok = (message) => console.log(`✓ ${++step}. ${message}`)

function watch(page, label) {
  page.on('pageerror', (error) => problems.push(`${label} PAGEERROR ${error.message.slice(0, 200)}`))
  page.on('console', (message) => {
    const expected = allowServerErrors ? /status of (400|422|500)/ : /status of (400|422)/
    if (message.type() === 'error' && !expected.test(message.text())) {
      problems.push(`${label} CONSOLE ${message.text().slice(0, 200)}`)
    }
  })
}

const money = (text) => Number(text.replace(/[^0-9.\-]/g, ''))

const context = await browser.newContext({ viewport: { width: 1440, height: 900 } })
const p = await context.newPage()
watch(p, 'desktop')

const bottom = () => p.locator('[role=tabpanel]').last()
const ticket = () => p.locator('section[aria-labelledby="ticket-heading"]')
const tab = (name) => p.locator('[role=tab]', { hasText: name })
const rail = (label) => p.locator(`nav[aria-label="Main"] a[aria-label="${label}"]`)
const stat = async (label) => money(await p.locator(`dt:text-is("${label}") + dd`).first().innerText())

/** Opens a symbol through the command palette, the way a person would. */
async function openSymbol(query, symbol) {
  await p.keyboard.press('Control+k')
  await p.locator('dialog[open]').waitFor()
  await p.locator('input[role=combobox]').fill(query)
  await p.locator(`[role=option]:has-text("${symbol}")`).first().waitFor()
  await p.keyboard.press('Enter')
  await p.locator('dialog[open]').waitFor({ state: 'detached' }).catch(() => {})
  await p.waitForURL(new RegExp(`symbol=${symbol}`))
  await p.locator(`section[aria-label="${symbol} chart"]`).waitFor()
}

async function blurFocus() {
  await p.evaluate(() => document.activeElement instanceof HTMLElement && document.activeElement.blur())
}

// 1. signup lands on the workspace
await p.goto(`${BASE}/signup`)
await p.fill('input[name=email]', email)
for (const field of await p.locator('input[type=password]').all()) await field.fill(password)
await p.click('button[type=submit]')
await p.waitForURL(`${BASE}/`)
await p.locator('section[aria-label="AAPL chart"] canvas').first().waitFor()
await p.locator('[data-testid=quote-price]').waitFor()
assert.equal(await p.evaluate(() => document.documentElement.dataset.theme), 'dark')
await p.locator('text=Nothing on your watchlist yet').waitFor()
ok('signup -> the workspace: an AAPL chart, an order ticket and an empty watchlist, in the dark theme')

// 2. the command palette, by Ctrl+K and by /
await openSymbol('msft', 'MSFT')
await p.locator('h2:text-is("MSFT")').waitFor()
await blurFocus()
await p.keyboard.press('/')
await p.locator('dialog[open]').waitFor()
await p.keyboard.press('Escape')
await p.locator('dialog[open]').waitFor({ state: 'detached' })
ok('Ctrl+K finds MSFT and opens it; / opens the palette and Esc closes it')

// 3. watch it, even when a slow refresh that began BEFORE the click answers AFTER it. Without care
// the late, older answer (an empty list) overwrites the newer one and the button flips back.
let held = null
await p.route('**/api/watchlist', async (route) => {
  if (route.request().method() === 'GET' && held === null) {
    held = route // hold the first refresh in flight
    return
  }
  await route.continue()
})
await p.evaluate(() => document.dispatchEvent(new Event('visibilitychange'))) // a refresh starts now
while (held === null) await p.waitForTimeout(50)
const staleAnswer = await held.fetch() // the server answers right now: an empty watchlist
await p.click('button:text-is("Watch")')
await p.locator('button:text-is("Unwatch")').waitFor()
await held.fulfill({ response: staleAnswer }) // and the browser receives that old answer late
await p.waitForTimeout(700)
assert.equal(await p.locator('button:text-is("Unwatch")').count(), 1, 'a late, older answer must not undo the watch')
await p.unroute('**/api/watchlist')
const watchRow = p.locator('section[aria-labelledby="watchlist-heading-rail"] tr:has-text("MSFT")')
await watchRow.waitFor()
assert.match(await watchRow.innerText(), /\$\d/)
await openSymbol('aapl', 'AAPL')
await p.locator('button[aria-label="Open MSFT"]').click()
await p.locator('h2:text-is("MSFT")').waitFor()
ok('watch -> MSFT is on the watchlist with a price even though an older refresh answered late; clicking its row opens it')

// 4. indicators, and what they say
await p.locator('summary:has-text("Indicators")').click()
await p.locator('label:has-text("SMA 20") input').check()
await p.locator('label:has-text("RSI 14") input').check()
await p.locator('[aria-label="Indicator readings"] li').first().waitFor({ timeout: 25000 })
const readings = await p.locator('[aria-label="Indicator readings"] li').allInnerTexts()
assert.ok(readings.some((r) => /RSI is/.test(r)), 'expected an RSI reading')
for (const reading of readings) assert.doesNotMatch(reading, /\b(buy|sell|bullish|bearish|should|recommend)\b/i)
await p.locator('summary:has-text("Indicators")').click()
ok(`indicators -> ${readings.length} neutral readings, none advisory`)

// 5. trade with the keyboard: B opens the ticket on the buy side with the cursor in Shares
await blurFocus()
await p.keyboard.press('b')
// Buy is already the default side, so "Buy is pressed" proves nothing: wait for the cursor itself.
await p.waitForFunction(() => document.activeElement?.id === 'quantity')
await p.keyboard.type('3')
const price = money(await p.locator('[data-testid=quote-price]').innerText())
assert.ok(price > 10 && price < 5000, `implausible price ${price}`)
const cashBefore = money(await ticket().locator('dt:text-is("Cash available") + dd').innerText())
await ticket().locator('button[type=submit]').click()
await p.locator('text=BUY 3 MSFT: filled').waitFor()
await p.locator('[role=tabpanel] tr:has-text("MSFT")').first().waitFor()
await ticket().locator('dt:text-is("You hold") + dd:has-text("3 shares")').waitFor()
await p.waitForFunction(
  (before) => Number(document.querySelector('section[aria-labelledby="ticket-heading"] dt + dd + dt + dd')?.textContent.replace(/[^0-9.\-]/g, '')) < before,
  cashBefore,
)
const cashText = () => ticket().locator('dt:text-is("Cash available") + dd').innerText().then(money)
const cashAfterBuy = await cashText()
assert.ok(Math.abs(cashBefore - cashAfterBuy - 3 * price) < 5, `cash ${cashBefore} -> ${cashAfterBuy} for 3 x ${price}`)
ok(`B -> buy ticket with the cursor in Shares; buy 3 MSFT filled at ~$${price}; holdings, position and cash all update`)

// 6. S switches the side
await blurFocus()
await p.keyboard.press('s')
await ticket().locator('button[aria-pressed=true]:text-is("Sell")').waitFor()
await ticket().locator('button:text-is("Buy")').click()
ok('S -> the same ticket on the sell side')

// 7. a limit far below the market waits, then is cancelled
await ticket().locator('[aria-label="Order type"] button:text-is("Limit")').click()
await ticket().locator('#quantity').fill('1')
await ticket().locator('#price').fill('50.00')
await ticket().locator('button[type=submit]').click()
await p.locator('text=BUY 1 MSFT: pending').waitFor()
await tab('Open orders').click()
await bottom().locator('tr:has-text("pending")').waitFor()
assert.equal(await cashText(), cashAfterBuy, 'a pending order must not move cash')
await bottom().locator('button:text-is("Cancel")').first().click()
await tab('Order history').click()
await bottom().locator('tr:has-text("cancelled")').waitFor()
ok('limit @ $50 -> pending with no cash moved, then cancelled, and it moves to the history')

// 8. a limit just above the market is filled by the scheduler, with no reload
const limit = Math.ceil(price * 1.05).toFixed(2)
await ticket().locator('#price').fill(limit)
await ticket().locator('button[type=submit]').click()
await p.locator('text=BUY 1 MSFT: pending').waitFor()
await tab('Holdings').click()
await p.waitForFunction(
  () => [...document.querySelectorAll('[role=tabpanel] tbody tr')].some((row) => row.textContent.includes('MSFT') && row.children[1]?.textContent.trim() === '4'),
  null,
  { timeout: 70000 },
)
ok(`limit @ $${limit} (above the $${price} market) -> filled by the scheduler; holdings show 4 shares with no page reload`)

// 9. a buy with a stop-loss attached creates the pending stop
await ticket().locator('[aria-label="Order type"] button:text-is("Market")').click()
await ticket().locator('label:has-text("stop-loss") input').check()
await ticket().locator('button[type=submit]').click()
await p.locator('text=BUY 1 MSFT: filled').waitFor()
await tab('Open orders').click()
const stopRow = bottom().locator('tr:has-text("Stop-loss")').first()
await stopRow.waitFor({ timeout: 20000 })
const stopPrice = money((await stopRow.innerText()).match(/stop \$([\d,.]+)/)[1])
assert.ok(Math.abs(stopPrice - price * 0.95) < price * 0.03, `stop ${stopPrice} vs ${price * 0.95}`)
ok(`buy + attached stop-loss -> a pending stop at $${stopPrice} (~5% below the fill)`)

// 10. a risk limit governs the next order, then resets
await p.click('button[aria-label="Account"]')
await p.locator('[role=menuitem]:has-text("Risk limits")').click()
await p.waitForURL(`${BASE}/risk`)
await p.locator('#risk-maxOrderValue').fill('100')
await p.click('button:text-is("Save limits")')
await p.locator('text=Saved.').waitFor()
await rail('Dashboard').click()
await p.locator('section[aria-label="MSFT chart"]').waitFor()
await ticket().locator('button[type=submit]').click()
await p.locator('text=Order rejected: order too large').waitFor()
await tab('Order history').click()
await bottom().locator('td:has-text("Rejected: order too large")').first().waitFor()
await rail('Risk limits').click()
await p.click('button:text-is("Reset to defaults")')
await p.locator('text=Saved.').waitFor()
assert.equal(await p.locator('#risk-maxOrderValue').inputValue(), '5000.00')
ok('risk limit $100 -> the next order is rejected and recorded; reset restores $5,000')

// 11. the portfolio page
await rail('Portfolio').click()
await p.locator('dt:text-is("Total value") + dd:has-text("$")').waitFor()
for (const label of ['Day P&L', 'Overall P&L', 'Invested', 'Cash']) {
  await p.locator(`dt:text-is("${label}")`).waitFor()
}
const total = await stat('Total value')
assert.ok(Math.abs(total - 100000) < 200, `total ${total}`)
await p.locator('th:has(button:text-is("Qty")) button').click()
assert.equal(await p.locator('th[aria-sort="descending"]').innerText().then((t) => t.replace(/[▲▼\s]/g, '')), 'QTY')
await p.locator('th:has(button:text-is("Symbol")) button').click()
assert.equal(await p.locator('th[aria-sort="ascending"]').innerText().then((t) => t.replace(/[▲▼\s]/g, '')), 'SYMBOL')
await p.locator('section[aria-labelledby="allocation-heading"] li:has-text("Cash")').waitFor()
await p.locator('text=Your value will start moving').waitFor()
await p.locator('[aria-label="Performance range"] button:text-is("1Y")').click()
await p.locator('[aria-label="Performance range"] button[aria-pressed=true]:text-is("1Y")').waitFor()
ok(`portfolio -> total ~$${total}, day and overall P&L, sortable holdings, an allocation legend with cash, and the range switches`)

// 12. the orders and watchlist pages
await rail('Orders').click()
await p.locator('[role=tab]:has-text("Order history")').click()
await p.locator('td:has-text("Rejected: order too large")').first().waitFor()
await rail('Watchlist').click()
await p.locator('tr:has-text("MSFT") a:has-text("Chart")').waitFor()
ok('the orders page keeps the whole history, including the rejection; the watchlist page links to the chart')

// 13. the theme is remembered
await rail('Dashboard').click()
await p.click('button[aria-label="Switch to the light theme"]')
assert.equal(await p.evaluate(() => document.documentElement.dataset.theme), 'light')
await p.reload()
await p.locator('section[aria-label$="chart"] canvas').first().waitFor()
assert.equal(await p.evaluate(() => document.documentElement.dataset.theme), 'light')
await p.click('button[aria-label="Switch to the dark theme"]')
assert.equal(await p.evaluate(() => document.documentElement.dataset.theme), 'dark')
ok('light theme -> chart restyled in place, remembered across a reload; back to dark')

// 14. panels collapse, resize and remember their size
const railToggle = p.locator('button[aria-label$="the watchlist and order panel"]')
await railToggle.click()
await p.waitForFunction(() => document.querySelector('section[aria-labelledby="ticket-heading"]')?.getBoundingClientRect().width === 0)
await railToggle.click()
await p.waitForFunction(() => document.querySelector('section[aria-labelledby="ticket-heading"]')?.getBoundingClientRect().width > 200)
const widthBefore = (await ticket().boundingBox()).width
const seam = p.locator('[role=separator][aria-orientation=vertical]').first()
const box = await seam.boundingBox()
await p.mouse.move(box.x + box.width / 2, box.y + box.height / 2)
await p.mouse.down()
await p.mouse.move(box.x - 120, box.y + box.height / 2, { steps: 8 })
await p.mouse.up()
const widthAfter = (await ticket().boundingBox()).width
assert.ok(widthAfter > widthBefore + 60, `expected the rail to widen: ${widthBefore} -> ${widthAfter}`)
await p.reload()
await ticket().waitFor()
await p.waitForTimeout(500)
const widthRemembered = (await ticket().boundingBox()).width
assert.ok(Math.abs(widthRemembered - widthAfter) < 6, `size not remembered: ${widthAfter} vs ${widthRemembered}`)
ok(`panels -> collapse and expand; dragging the seam widens the rail ${Math.round(widthBefore)} to ${Math.round(widthAfter)}px, and the size survives a reload`)

// 15. a failing endpoint shows an error in its own place, with a Retry that works
allowServerErrors = true
const failing = (route) => route.fulfill({ status: 500, contentType: 'application/json', body: '{"message":"boom"}' })
// A refresh that fails while figures are already on screen keeps them (the poll will try again)...
await rail('Watchlist').click()
await p.locator('tr:has-text("MSFT")').first().waitFor()
await p.route('**/api/watchlist', failing)
await p.evaluate(() => document.dispatchEvent(new Event('visibilitychange')))
await p.waitForTimeout(800)
assert.equal(await p.locator('tr:has-text("MSFT")').count() > 0, true, 'a failed refresh must not blank the table')
// ...but a first load that fails has nothing to show, so it says so, with a Retry in its place.
await p.goto(`${BASE}/watchlist`)
await p.locator('button:text-is("Retry")').first().waitFor()
assert.equal(await p.locator('[role=alert]').count() > 0, true)
await p.unroute('**/api/watchlist')
await p.locator('button:text-is("Retry")').first().click()
await p.locator('tr:has-text("MSFT")').first().waitFor()
allowServerErrors = false
ok('a failing /api/watchlist -> a failed refresh keeps the table; a failed first load shows an error with Retry, and Retry brings the table back')

// 16. everything survives a reload; log out; log back in with the address typed differently
await p.reload()
await rail('Portfolio').click()
await p.locator('dt:text-is("Total value") + dd:has-text("$")').waitFor()
await p.click('button[aria-label="Account"]')
await p.locator('[role=menuitem]:text-is("Log out")').click()
await p.waitForURL(`${BASE}/login`)
await p.fill('input[name=email]', `  ${email.toUpperCase()}  `)
await p.fill('input[type=password]', password)
await p.click('button[type=submit]')
await p.waitForURL(/localhost:\d+\/(\?.*)?$/)
await p.locator('section[aria-label$="chart"]').first().waitFor()
await rail('Portfolio').click()
await p.locator('dt:text-is("Total value") + dd:has-text("$")').waitFor()
ok('reload and log out -> log in with a padded, upper-case email -> the same account and portfolio')

// 17. no token, no access
await p.evaluate(() => localStorage.removeItem('portfoliopro.token'))
await p.goto(`${BASE}/portfolio`)
await p.waitForURL(`${BASE}/login`)
ok('no token -> redirected to login')

// 18. on a phone the panels are tabs and the rail is a bar along the bottom
const phone = await browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true })
const m = await phone.newPage()
watch(m, 'phone')
await m.goto(`${BASE}/login`)
await m.fill('input[name=email]', email)
await m.fill('input[type=password]', password)
await m.click('button[type=submit]')
await m.locator('[role=tablist][aria-label="Workspace"]').waitFor()
// Two navs exist in the page (the icon rail and the bottom bar); on a phone exactly one is showing.
const visibleNav = m.locator('nav[aria-label="Main"]:visible')
assert.equal(await visibleNav.count(), 1)
assert.equal(await visibleNav.locator('a').count(), 5)
assert.ok((await visibleNav.boundingBox()).y > 600, 'the bar should run along the bottom')
for (const name of ['Chart', 'Watchlist', 'Order', 'Positions']) {
  await m.locator(`[role=tablist][aria-label="Workspace"] [role=tab]:text-is("${name}")`).waitFor()
}
await m.locator('[role=tab]:text-is("Order")').click()
await m.locator('#quantity').waitFor()
await m.locator('[role=tab]:text-is("Positions")').click()
await m.locator('[role=tabpanel] tr:has-text("MSFT")').first().waitFor()
assert.equal(await m.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth), 0)
ok('phone -> the workspace is four tabs with a bottom bar, the ticket and the positions are reachable, and nothing overflows sideways')
await phone.close()

assert.deepEqual(problems, [], 'unexpected browser errors: ' + problems.join(' | '))
ok('no unexpected console or page errors in the whole run')
await browser.close()
console.log(`\nJOURNEY PASSED (${step} checks) as ${email}`)
