/**
 * A colour slot per holding that outlives a render, so a stock keeps its colour while others come
 * and go. Colour follows the entity, never its rank: recolouring the survivors whenever the mix
 * changes would tell a reader the wrong thing.
 *
 * Only the first `slotCount` distinct holdings get a slot. The rest have none and are folded into
 * "Other" by the caller, since a chart's categorical palette is finite.
 *
 * Each chart makes its own assigner, so a donut limited to four slots and a bar limited to six
 * do not disturb each other's memory.
 */
export function createSlotAssigner(slotCount: number) {
  const remembered = new Map<string, number>()

  return (symbols: string[]): ReadonlyMap<string, number> => {
    const present = new Set(symbols)
    for (const symbol of [...remembered.keys()]) {
      if (!present.has(symbol)) {
        remembered.delete(symbol) // sold: its slot is free for the next new holding
      }
    }
    const used = new Set(remembered.values())
    for (const symbol of symbols) {
      if (remembered.has(symbol)) {
        continue
      }
      let slot = 0
      while (used.has(slot)) {
        slot += 1
      }
      if (slot < slotCount) {
        remembered.set(symbol, slot)
        used.add(slot)
      }
    }
    return new Map(remembered)
  }
}
