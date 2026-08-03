import { describe, expect, it } from 'vitest'
import { QueryClient } from '@tanstack/react-query'
import { DEFAULT_WEIGHT_RANGE, weightsKey, weightsKeyPrefix } from './weightQueryKeys'

// These assert against a real QueryClient rather than against the shape of the arrays, because the
// bug they exist to catch was about React Query's matching rules, not about the arrays themselves:
// a filter key matches a cached key only when it is a PREFIX of it, so invalidating a key with the
// range appended could never reach a key without one.

describe('weight query keys', () => {
  it('gives the panel and the goals board the same cache entry', () => {
    // GoalsBoard reads the default window; WeightPanel starts on it. One request, one entry — built
    // separately these drifted apart and the series was fetched twice on every visit.
    expect(weightsKey(null, DEFAULT_WEIGHT_RANGE)).toEqual(weightsKey(null, 'QUARTER'))
    expect(weightsKey('athlete-1', DEFAULT_WEIGHT_RANGE)).toEqual(weightsKey('athlete-1', 'QUARTER'))
  })

  it('keeps one client’s readings away from another’s', () => {
    expect(weightsKey('athlete-1', 'QUARTER')).not.toEqual(weightsKey('athlete-2', 'QUARTER'))
    expect(weightsKey(null, 'QUARTER')).not.toEqual(weightsKey('athlete-1', 'QUARTER'))
  })

  it('invalidates every window from the prefix', async () => {
    // A new reading moves every window that contains today — which is all of them — and the goals
    // board along with them. Invalidating one range's key alone left the board showing the weight
    // from before the client stepped on the scale.
    const client = new QueryClient()
    client.setQueryData(weightsKey(null, 'QUARTER'), { points: [] })
    client.setQueryData(weightsKey(null, 'YEAR'), { points: [] })
    client.setQueryData(weightsKey(null, 'ALL'), { points: [] })

    await client.invalidateQueries({ queryKey: weightsKeyPrefix(null) })

    for (const range of ['QUARTER', 'YEAR', 'ALL'] as const) {
      expect(client.getQueryState(weightsKey(null, range))?.isInvalidated).toBe(true)
    }
  })

  it('does not invalidate another client’s readings', async () => {
    const client = new QueryClient()
    client.setQueryData(weightsKey('athlete-1', 'QUARTER'), { points: [] })
    client.setQueryData(weightsKey('athlete-2', 'QUARTER'), { points: [] })

    await client.invalidateQueries({ queryKey: weightsKeyPrefix('athlete-1') })

    expect(client.getQueryState(weightsKey('athlete-1', 'QUARTER'))?.isInvalidated).toBe(true)
    expect(client.getQueryState(weightsKey('athlete-2', 'QUARTER'))?.isInvalidated).toBe(false)
  })
})
