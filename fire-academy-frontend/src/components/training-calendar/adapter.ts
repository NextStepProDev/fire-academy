import type { QueryKey } from '@tanstack/react-query'
import { adminApi } from '../../api/admin'
import { myTrainingApi } from '../../api/user'
import type {
  CalendarRange, CreateTrainingBody, PasteMode, PersonalTraining, TrainingComment, UpdateTrainingBody,
} from '../../types'

/**
 * How the shared calendar reaches its data.
 *
 * The contract is deliberately limited to DATA ACCESS. No render functions, no labels, no "should I
 * show X" flags — the reference implementation started that way and the adapter grew into a second
 * component. Anything that differs visually between the coach and the client is rendered by the page
 * AROUND <TrainingCalendar/>, never inside it.
 *
 * Optional methods double as the permission matrix: an absent method means the action does not exist
 * for this role, so there is no separate list of capabilities to fall out of sync.
 */
export interface TrainingCalendarAdapter {
  role: 'coach' | 'athlete'
  athleteId: string
  rangeKey: (from: string, to: string) => QueryKey
  fetchRange: (from: string, to: string) => Promise<CalendarRange>
  createTraining: (body: CreateTrainingBody) => Promise<PersonalTraining>
  updateTraining: (id: string, body: UpdateTrainingBody) => Promise<PersonalTraining>
  deleteTraining: (id: string) => Promise<void>
  duplicateTraining: (id: string, offsetDays?: number) => Promise<PersonalTraining>
  pasteTraining: (sourceId: string, targetDate: string, mode: PasteMode) => Promise<PersonalTraining>
  /** Ticking off is the client's act alone — absent for the coach, so no button renders. */
  completeTraining?: (id: string, body: { rpe: number; feedback?: string | null }) => Promise<PersonalTraining>
  uncompleteTraining?: (id: string) => Promise<PersonalTraining>
  getComments: (trainingId: string) => Promise<TrainingComment[]>
  addComment: (trainingId: string, body: string) => Promise<TrainingComment>
  markSeen: () => Promise<void>
  dismissDeletions: () => Promise<void>
}

export function coachAdapter(athleteId: string): TrainingCalendarAdapter {
  return {
    role: 'coach',
    athleteId,
    // The athlete id is part of the key: switching between two clients is a different entity, not
    // fresh data for the same one, so their pages must never bleed into each other.
    rangeKey: (from, to) => ['admin', 'training-calendar', athleteId, from, to],
    fetchRange: (from, to) => adminApi.getTrainingCalendar(athleteId, from, to),
    createTraining: (body) => adminApi.createPersonalTraining(athleteId, body),
    updateTraining: (id, body) => adminApi.updatePersonalTraining(id, body),
    deleteTraining: (id) => adminApi.deletePersonalTraining(id),
    duplicateTraining: (id, offsetDays) => adminApi.duplicatePersonalTraining(id, offsetDays),
    pasteTraining: (sourceId, targetDate, mode) => adminApi.pastePersonalTraining(sourceId, targetDate, mode),
    getComments: (id) => adminApi.getTrainingComments(id),
    addComment: (id, body) => adminApi.addTrainingComment(id, body),
    markSeen: () => adminApi.markTrainingCalendarSeen(athleteId),
    dismissDeletions: () => adminApi.dismissTrainingDeletions(athleteId),
  }
}

export function athleteAdapter(athleteId: string): TrainingCalendarAdapter {
  return {
    role: 'athlete',
    athleteId,
    rangeKey: (from, to) => ['user', 'my-training', 'calendar', from, to],
    fetchRange: (from, to) => myTrainingApi.getCalendar(from, to),
    createTraining: (body) => myTrainingApi.createTraining(body),
    updateTraining: (id, body) => myTrainingApi.updateTraining(id, body),
    deleteTraining: (id) => myTrainingApi.deleteTraining(id),
    duplicateTraining: (id, offsetDays) => myTrainingApi.duplicateTraining(id, offsetDays),
    pasteTraining: (sourceId, targetDate, mode) => myTrainingApi.pasteTraining(sourceId, targetDate, mode),
    completeTraining: (id, body) => myTrainingApi.completeTraining(id, body),
    uncompleteTraining: (id) => myTrainingApi.uncompleteTraining(id),
    getComments: (id) => myTrainingApi.getComments(id),
    addComment: (id, body) => myTrainingApi.addComment(id, body),
    markSeen: () => myTrainingApi.markSeen(),
    dismissDeletions: () => myTrainingApi.dismissDeletions(),
  }
}
