import { fetchApi } from './client'

/**
 * Where a private note is pinned.
 *
 * <p>A discriminated union rather than four loose props: a `session` anchor cannot be built without
 * the person and the day, because the compiler will not let it. That matters — a session has no id
 * of its own, so those two fields ARE half of its address.
 */
export type NoteAnchor =
  | { target: 'training'; id: string }
  | { target: 'event'; id: string }
  | { target: 'slot'; id: string }
  | { target: 'session'; id: string; athleteId: string; date: string }

export interface PrivateNote {
  body: string | null
  updatedAt: string | null
}

export interface NoteMarkers {
  slotIds: string[]
  eventIds: string[]
  trainingIds: string[]
  sessions: { slotId: string; date: string }[]
}

/** URL and cache key come from the same anchor, so they cannot drift apart. */
export function notePath(anchor: NoteAnchor): string {
  const base = `/admin/notes/${anchor.target}/${anchor.id}`
  return anchor.target === 'session'
    ? `${base}?athleteId=${anchor.athleteId}&date=${anchor.date}`
    : base
}

export function noteKey(anchor: NoteAnchor): string[] {
  return anchor.target === 'session'
    ? ['admin', 'notes', anchor.target, anchor.id, anchor.athleteId, anchor.date]
    : ['admin', 'notes', anchor.target, anchor.id]
}

/** Invalidating this prefix relights markers too — invalidating one note's key would not. */
export const NOTES_KEY_PREFIX = ['admin', 'notes']

export const notesApi = {
  get: (anchor: NoteAnchor) => fetchApi<PrivateNote>(notePath(anchor)),

  save: (anchor: NoteAnchor, body: string) =>
    fetchApi<void>(notePath(anchor), { method: 'PUT', body: JSON.stringify({ body }) }),

  remove: (anchor: NoteAnchor) => fetchApi<void>(notePath(anchor), { method: 'DELETE' }),

  /**
   * Markers for a page. The window is optional: a calendar always has one, the admin lists do not
   * (their contents are open-ended in both directions). An athlete-scoped call requires it.
   */
  markers: (from?: string, to?: string, athleteId?: string) => {
    const params = new URLSearchParams()
    if (from) params.set('from', from)
    if (to) params.set('to', to)
    if (athleteId) params.set('athleteId', athleteId)
    return fetchApi<NoteMarkers>(`/admin/notes/markers?${params}`)
  },
}
