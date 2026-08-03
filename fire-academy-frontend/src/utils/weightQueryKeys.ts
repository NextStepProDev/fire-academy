// One definition of "where the weight series lives in the cache".
//
// Two components read the same series: WeightPanel, which owns the chart and the range picker, and
// GoalsBoard, which needs the current trend to draw a weight goal's progress bar. They used to build
// their keys separately, and the keys did not match — the panel's carried the range, the board's did
// not. Same URL, two cache entries, with two consequences: the series was fetched twice on every
// visit, and a weigh-in invalidated only the panel's copy, so the progress bar kept showing the
// weight from before the client stepped on the scale until the page was reloaded.
//
// The prefix exists for the second half of that. Invalidating one range's key cannot match another
// range's, and a new reading changes every window that contains today — which is all of them.

import type { QueryKey } from '@tanstack/react-query'
import type { WeightRange } from '../types'

/** What both the server and the API client fall back to when nobody picks a window. */
export const DEFAULT_WEIGHT_RANGE: WeightRange = 'QUARTER'

/** Every window of one person's readings. Invalidate here — a new reading moves all of them. */
export function weightsKeyPrefix(athleteId: string | null): QueryKey {
  return athleteId != null
    ? ['admin', 'weights', athleteId]
    : ['user', 'my-training', 'weights']
}

/**
 * One window of one person's readings. The range goes last so `keepWithinEntity(..., 1)` can tell
 * "same person, wider window" (keep the chart on screen) from "different person" (never).
 */
export function weightsKey(athleteId: string | null, range: WeightRange): QueryKey {
  return [...weightsKeyPrefix(athleteId), range]
}
