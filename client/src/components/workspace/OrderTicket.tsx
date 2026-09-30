import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'

import { errorMessage } from '../../api/client'
import { placeOrder } from '../../api/trading'
import { useTradingData } from '../../data/useTradingData'
import { estimateValue, formatUsd } from '../../lib/money'
import type { useQuote } from '../../lib/useQuote'
import type { OrderSide, OrderType, PlaceOrderRequest } from '../../types/trading'

const ORDER_TYPES: { value: OrderType; label: string }[] = [
  { value: 'MARKET', label: 'Market' },
  { value: 'LIMIT', label: 'Limit' },
  { value: 'STOP_LOSS', label: 'Stop-loss' },
]

interface OrderTicketProps {
  symbol: string
  quote: ReturnType<typeof useQuote>
}

/**
 * Buy or sell the symbol on screen. B and S (anywhere) open it on that side, with the cursor in
 * the quantity box. It shows what an order would cost and what is available to spend or sell, and
 * an order the risk limits turn away says so here and stays in the order history.
 */
export function OrderTicket({ symbol, quote }: OrderTicketProps) {
  const { portfolio, reloadAll } = useTradingData()
  const [params, setParams] = useSearchParams()
  const [side, setSide] = useState<OrderSide>('BUY')
  const [orderType, setOrderType] = useState<OrderType>('MARKET')
  const [quantity, setQuantity] = useState('1')
  const [price, setPrice] = useState('')
  const [attachStopLoss, setAttachStopLoss] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [handledSide, setHandledSide] = useState<string | null>(null)
  const quantityRef = useRef<HTMLInputElement>(null)

  // B or S sets the side. Set while rendering (React's pattern for state that follows a prop), so
  // the ticket is on the right side in the same frame the shortcut arrives, with no effect flicker.
  const sideParam = params.get('side')
  if (sideParam !== handledSide) {
    setHandledSide(sideParam)
    if (sideParam === 'BUY' || sideParam === 'SELL') {
      setSide(sideParam)
    }
  }
  // Then take the cursor to the quantity and drop the param, so a reload does not re-trigger it.
  useEffect(() => {
    if (sideParam === 'BUY' || sideParam === 'SELL') {
      quantityRef.current?.focus()
      quantityRef.current?.select()
      setParams(
        (previous) => {
          const updated = new URLSearchParams(previous)
          updated.delete('side')
          return updated
        },
        { replace: true },
      )
    }
  }, [sideParam, setParams])

  const shares = Number(quantity)
  const referencePrice = orderType === 'MARKET' ? quote.quote?.price : price
  const estimate = referencePrice === undefined ? null : estimateValue(referencePrice, shares)
  const held = portfolio.data?.holdings.find((holding) => holding.symbol === symbol)?.quantity ?? 0

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!Number.isInteger(shares) || shares < 1) {
      setError('Quantity must be a whole number of shares')
      return
    }
    const request: PlaceOrderRequest = { symbol, side, type: orderType, quantity: shares }
    if (orderType !== 'MARKET') {
      // Kept as the text the user typed: money is never parsed into a number (rule 1).
      if (!/^\d+(\.\d{1,4})?$/.test(price) || Number(price) <= 0) {
        setError('Enter a price greater than zero, with at most 4 decimal places')
        return
      }
      if (orderType === 'LIMIT') {
        request.limitPrice = price
      } else {
        request.triggerPrice = price
      }
    }
    if (side === 'BUY' && orderType !== 'STOP_LOSS' && attachStopLoss) {
      request.attachStopLoss = true
    }
    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      const order = await placeOrder(request)
      setNotice(`${order.side} ${order.quantity} ${order.symbol}: ${order.status.toLowerCase()}`)
    } catch (failure) {
      // A rejected order is a 422 with the reason in the message, and is on the order list too.
      setError(errorMessage(failure))
    } finally {
      setBusy(false)
      void reloadAll()
    }
  }

  return (
    <section aria-labelledby="ticket-heading" className="panel h-full">
      <div className="panel-header">
        <h2 id="ticket-heading">Order · {symbol}</h2>
        <span className="hidden items-center gap-1 normal-case tracking-normal sm:flex">
          <kbd className="kbd">B</kbd>
          <kbd className="kbd">S</kbd>
        </span>
      </div>
      <form onSubmit={handleSubmit} className="flex min-h-0 flex-1 flex-col gap-2 overflow-auto p-2.5">
        <div className="seg w-full" role="group" aria-label="Side">
          {(['BUY', 'SELL'] as const).map((option) => (
            <button
              key={option}
              type="button"
              className="flex-1"
              aria-pressed={side === option}
              disabled={orderType === 'STOP_LOSS' && option === 'BUY'}
              onClick={() => setSide(option)}
            >
              {option === 'BUY' ? 'Buy' : 'Sell'}
            </button>
          ))}
        </div>

        <div className="seg w-full" role="group" aria-label="Order type">
          {ORDER_TYPES.map((option) => (
            <button
              key={option.value}
              type="button"
              className="flex-1"
              aria-pressed={orderType === option.value}
              onClick={() => {
                setOrderType(option.value)
                // A stop-loss only ever sells.
                if (option.value === 'STOP_LOSS') {
                  setSide('SELL')
                }
              }}
            >
              {option.label}
            </button>
          ))}
        </div>

        <div className="grid grid-cols-2 gap-2">
          <div>
            <label htmlFor="quantity" className="label block">Shares</label>
            <input
              ref={quantityRef}
              id="quantity"
              type="number"
              min={1}
              max={1000000}
              step={1}
              value={quantity}
              onChange={(event) => setQuantity(event.target.value)}
              className="input mt-0.5 tabular-nums"
            />
          </div>
          {orderType !== 'MARKET' && (
            <div>
              <label htmlFor="price" className="label block">{orderType === 'LIMIT' ? 'Limit price' : 'Trigger price'}</label>
              <input
                id="price"
                inputMode="decimal"
                placeholder="0.00"
                value={price}
                onChange={(event) => setPrice(event.target.value)}
                className="input mt-0.5 tabular-nums"
              />
            </div>
          )}
        </div>

        {orderType === 'LIMIT' && (
          <p className="text-xs text-ink-3">
            A {side === 'BUY' ? 'buy fills at or below' : 'sell fills at or above'} this price, at the price then available.
            It waits as pending until then.
          </p>
        )}
        {orderType === 'STOP_LOSS' && (
          <p className="text-xs text-ink-3">Sells once the price falls to this trigger, at the price then available, which can be lower.</p>
        )}
        {side === 'BUY' && orderType !== 'STOP_LOSS' && (
          <label className="flex min-h-6 items-center gap-2 text-sm text-ink-2 pointer-coarse:min-h-11">
            <input type="checkbox" checked={attachStopLoss} onChange={(event) => setAttachStopLoss(event.target.checked)} className="size-3.5 accent-accent" />
            Also place a stop-loss below the fill price
          </label>
        )}

        <dl className="grid grid-cols-[1fr_auto] gap-x-3 gap-y-0.5 border-t border-rule pt-2 text-sm">
          <dt className="text-ink-3">{side === 'BUY' ? 'Estimated cost' : 'Estimated proceeds'}</dt>
          <dd className="text-right font-medium tabular-nums">{estimate ?? '—'}</dd>
          <dt className="text-ink-3">Cash available</dt>
          <dd className="text-right tabular-nums">{portfolio.data === null ? '—' : formatUsd(portfolio.data.cash)}</dd>
          <dt className="text-ink-3">You hold</dt>
          <dd className="text-right tabular-nums">{portfolio.data === null ? '—' : `${held} ${held === 1 ? 'share' : 'shares'}`}</dd>
        </dl>

        <button type="submit" disabled={busy} className="btn btn-primary min-h-8 w-full text-base pointer-coarse:min-h-11">
          {busy ? 'Placing…' : `${side === 'BUY' ? 'Buy' : 'Sell'} ${symbol}${orderType === 'MARKET' ? '' : orderType === 'LIMIT' ? ' limit' : ' stop-loss'}`}
        </button>

        {error !== null && <p role="alert" className="notice-error">{error}</p>}
        {notice !== null && <p role="status" className="notice-ok">{notice}</p>}
      </form>
    </section>
  )
}
