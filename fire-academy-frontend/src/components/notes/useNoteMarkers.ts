import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { notesApi } from '../../api/notes'
import { SHORT_STALE_MS } from '../../utils/queryFreshness'

/**
 * Which slots and terms already carry one of MY notes.
 *
 * Deliberately unranged: the events tab runs to whatever is scheduled and the archive runs backwards
 * forever, so no window of a sane width covers either. Safe because it is one author's identifiers
 * and never any text — the note itself still comes from its own endpoint, one row at a time.
 *
 * One query for a whole list, not one per row: the admin panel already learned that lesson.
 */
export function useNoteMarkers() {
  const { data } = useQuery({
    queryKey: ['admin', 'notes', 'markers', 'all'],
    queryFn: () => notesApi.markers(),
    staleTime: SHORT_STALE_MS,
    // The only person who can change these markers is the admin looking at them, in this tab, and
    // their own mutations invalidate the prefix. A focus refetch would just add a request to a
    // bucket the whole admin panel shares.
    refetchOnWindowFocus: false,
  })
  // Memoised so the identity survives a re-render: a fresh Set on every pass would defeat any
  // memoisation a caller adds later, and this hook feeds list rows.
  return useMemo(() => ({
    notedSlotIds: new Set(data?.slotIds ?? []),
    notedEventIds: new Set(data?.eventIds ?? []),
  }), [data])
}
