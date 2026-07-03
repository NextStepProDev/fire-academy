import { fetchApi } from './client'
import type { EventCategory, Instructor, EventType, EventInstance, Enrollment, AdminUser, PagedUsers, AdminUserDetail, TrainingSlot, TrainingRosterEntry, AdminUserSummary, CancelledSession, CancelledSessionOverview, DeletedTrainingSlot, TrainingHoliday, RefundEntry, UserMonthlyPayment } from '../types'
import { validateImageFile, compressImage } from '../utils/imageUtils'

export type EmailAudience = 'MARKETING' | 'ALL' | 'SELECTED'

interface SendUserEmailRequest {
  subject: string
  message: string
  audience: EmailAudience
  userIds?: string[]
}

interface TrainingSlotRequest {
  eventTypeId: string
  instructorId?: string
  dayOfWeek: number
  startTime: string
  endTime?: string
  price?: number
  maxParticipants: number
}

interface AdminAddTrainingEnrollmentRequest {
  userId: string
  startMonth: string
  months?: number
}

interface TrainingSlotRow {
  dayOfWeek: number
  startTime: string
  endTime?: string
  price?: number
  maxParticipants: number
}

interface BatchCreateTrainingSlotsRequest {
  eventTypeId: string
  instructorId?: string
  slots: TrainingSlotRow[]
}

interface CreateInstructorRequest {
  firstName: string
  lastName: string
  bio?: string
  categories: EventCategory[]
}

interface UpdateInstructorRequest {
  firstName: string
  lastName: string
  bio?: string
  categories: EventCategory[]
}

interface CreateEventTypeRequest {
  category: EventCategory
  name: string
  description?: string
}

interface UpdateEventTypeRequest {
  name: string
  description?: string
}

interface CreateEventRequest {
  eventTypeId?: string
  customName?: string
  description?: string
  category: EventCategory
  startDate: string
  endDate?: string
  startTime?: string
  endTime?: string
  location?: string
  price?: number
  maxParticipants?: number
}

interface UpdateEventRequest {
  eventTypeId?: string
  customName?: string
  description?: string
  startDate: string
  endDate?: string
  startTime?: string
  endTime?: string
  location?: string
  price?: number
  maxParticipants?: number
}

interface AdminEnrollRequest {
  eventId: string
  userId: string
  note?: string
}

export const adminApi = {
  // Instructors
  getInstructors: () =>
    fetchApi<Instructor[]>('/admin/instructors'),
  createInstructor: (data: CreateInstructorRequest) =>
    fetchApi<Instructor>('/admin/instructors', { method: 'POST', body: JSON.stringify(data) }),
  updateInstructor: (id: string, data: UpdateInstructorRequest) =>
    fetchApi<Instructor>(`/admin/instructors/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  deleteInstructor: (id: string) =>
    fetchApi<void>(`/admin/instructors/${id}`, { method: 'DELETE' }),
  uploadInstructorPhoto: async (id: string, file: File) => {
    const error = validateImageFile(file)
    if (error) throw new Error(error)
    const compressed = await compressImage(file)
    const formData = new FormData()
    formData.append('file', compressed)
    return fetchApi<Instructor>(`/admin/instructors/${id}/photo`, { method: 'POST', body: formData })
  },
  toggleInstructorActive: (id: string) =>
    fetchApi<Instructor>(`/admin/instructors/${id}/toggle-active`, { method: 'PATCH' }),
  reorderInstructor: (id: string, direction: string) =>
    fetchApi<void>(`/admin/instructors/${id}/reorder`, { method: 'POST', body: JSON.stringify({ direction }) }),

  // Event Types
  getEventTypes: (category: EventCategory) =>
    fetchApi<EventType[]>(`/admin/event-types?category=${category}`),
  createEventType: (data: CreateEventTypeRequest) =>
    fetchApi<EventType>('/admin/event-types', { method: 'POST', body: JSON.stringify(data) }),
  updateEventType: (id: string, data: UpdateEventTypeRequest) =>
    fetchApi<EventType>(`/admin/event-types/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  deleteEventType: (id: string) =>
    fetchApi<void>(`/admin/event-types/${id}`, { method: 'DELETE' }),
  uploadEventTypeThumbnail: async (id: string, file: File) => {
    const error = validateImageFile(file)
    if (error) throw new Error(error)
    const compressed = await compressImage(file)
    const formData = new FormData()
    formData.append('file', compressed)
    return fetchApi<EventType>(`/admin/event-types/${id}/thumbnail`, { method: 'POST', body: formData })
  },
  addEventTypePhoto: async (id: string, file: File) => {
    const error = validateImageFile(file)
    if (error) throw new Error(error)
    const compressed = await compressImage(file)
    const formData = new FormData()
    formData.append('file', compressed)
    return fetchApi<EventType>(`/admin/event-types/${id}/photos`, { method: 'POST', body: formData })
  },
  deleteEventTypePhoto: (id: string, photoId: string) =>
    fetchApi<void>(`/admin/event-types/${id}/photos/${photoId}`, { method: 'DELETE' }),
  reorderEventTypePhoto: (id: string, photoId: string, direction: string) =>
    fetchApi<void>(`/admin/event-types/${id}/photos/${photoId}/reorder`, { method: 'POST', body: JSON.stringify({ direction }) }),
  toggleEventTypeActive: (id: string) =>
    fetchApi<EventType>(`/admin/event-types/${id}/toggle-active`, { method: 'PATCH' }),
  reorderEventType: (id: string, direction: string) =>
    fetchApi<void>(`/admin/event-types/${id}/reorder`, { method: 'POST', body: JSON.stringify({ direction }) }),

  // Events
  getEvents: (category: EventCategory) =>
    fetchApi<EventInstance[]>(`/admin/events?category=${category}`),
  createEvent: (data: CreateEventRequest) =>
    fetchApi<EventInstance>('/admin/events', { method: 'POST', body: JSON.stringify(data) }),
  updateEvent: (id: string, data: UpdateEventRequest) =>
    fetchApi<EventInstance>(`/admin/events/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  deleteEvent: (id: string, force = false) =>
    fetchApi<void>(`/admin/events/${id}?force=${force}`, { method: 'DELETE' }),
  toggleEventActive: (id: string) =>
    fetchApi<EventInstance>(`/admin/events/${id}/toggle-active`, { method: 'PATCH' }),

  // Training slots (recurring)
  getTrainingSlots: (month: string) =>
    fetchApi<TrainingSlot[]>(`/admin/training-slots?month=${month}`),
  createTrainingSlot: (data: TrainingSlotRequest) =>
    fetchApi<TrainingSlot>('/admin/training-slots', { method: 'POST', body: JSON.stringify(data) }),
  createTrainingSlotsBatch: (data: BatchCreateTrainingSlotsRequest) =>
    fetchApi<TrainingSlot[]>('/admin/training-slots/batch', { method: 'POST', body: JSON.stringify(data) }),
  updateTrainingSlot: (id: string, data: TrainingSlotRequest) =>
    fetchApi<TrainingSlot>(`/admin/training-slots/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  deleteTrainingSlot: (id: string) =>
    fetchApi<void>(`/admin/training-slots/${id}`, { method: 'DELETE' }),
  toggleTrainingSlotActive: (id: string) =>
    fetchApi<TrainingSlot>(`/admin/training-slots/${id}/toggle-active`, { method: 'PATCH' }),
  deactivateTrainingSlot: (id: string, from: string) =>
    fetchApi<TrainingSlot>(`/admin/training-slots/${id}/deactivate`, { method: 'POST', body: JSON.stringify({ from }) }),
  reactivateTrainingSlot: (id: string) =>
    fetchApi<TrainingSlot>(`/admin/training-slots/${id}/reactivate`, { method: 'POST' }),

  // Training enrollments (roster / management)
  getTrainingRoster: (slotId: string, month: string) =>
    fetchApi<TrainingRosterEntry[]>(`/admin/training-slots/${slotId}/enrollments?month=${month}`),
  addTrainingEnrollment: (slotId: string, data: AdminAddTrainingEnrollmentRequest) =>
    fetchApi<void>(`/admin/training-slots/${slotId}/enrollments`, { method: 'POST', body: JSON.stringify(data) }),
  removeTrainingEnrollment: (id: string) =>
    fetchApi<void>(`/admin/training-enrollments/${id}`, { method: 'DELETE' }),
  setTrainingPayment: (id: string, data: { month: string; paid: boolean }) =>
    fetchApi<void>(`/admin/training-enrollments/${id}/payment`, { method: 'PUT', body: JSON.stringify(data) }),

  // Monthly payments grouped by person
  getMonthlyPayments: (month: string) =>
    fetchApi<UserMonthlyPayment[]>(`/admin/training-payments?month=${month}`),
  payUserMonth: (userId: string, month: string, paid: boolean) =>
    fetchApi<void>(`/admin/training-payments/pay-user/${userId}`, { method: 'POST', body: JSON.stringify({ month, paid }) }),

  // Cancelling individual sessions
  getCancelledSessions: (slotId: string) =>
    fetchApi<CancelledSession[]>(`/admin/training-slots/${slotId}/cancelled-sessions`),
  getCancelledSessionsOverview: () =>
    fetchApi<CancelledSessionOverview[]>(`/admin/training-slots/cancelled-sessions/overview`),
  cancelTrainingSession: (slotId: string, sessionDate: string) =>
    fetchApi<void>(`/admin/training-slots/${slotId}/cancel-session`, { method: 'POST', body: JSON.stringify({ sessionDate }) }),
  restoreTrainingSession: (slotId: string, date: string) =>
    fetchApi<void>(`/admin/training-slots/${slotId}/cancel-session?date=${date}`, { method: 'DELETE' }),
  cancelInstructorDay: (instructorId: string, date: string) =>
    fetchApi<{ cancelled: number }>(`/admin/training-slots/cancel-instructor-day`, { method: 'POST', body: JSON.stringify({ instructorId, date }) }),

  // Days off (whole-club closures)
  getTrainingHolidays: (month: string) =>
    fetchApi<TrainingHoliday[]>(`/admin/training-holidays?month=${month}`),
  addTrainingHoliday: (data: { date: string; label?: string }) =>
    fetchApi<TrainingHoliday>('/admin/training-holidays', { method: 'POST', body: JSON.stringify(data) }),
  removeTrainingHoliday: (id: string) =>
    fetchApi<void>(`/admin/training-holidays/${id}`, { method: 'DELETE' }),

  // Refunds ("Zwroty")
  getTrainingRefunds: (settled = false) =>
    fetchApi<RefundEntry[]>(`/admin/training-refunds?settled=${settled}`),
  settleRefund: (id: string, settlementType: 'REFUNDED' | 'CREDITED') =>
    fetchApi<void>(`/admin/training-refunds/${id}/settle`, { method: 'POST', body: JSON.stringify({ settlementType }) }),
  settleUserRefunds: (userId: string, settlementType: 'REFUNDED' | 'CREDITED') =>
    fetchApi<void>(`/admin/training-refunds/settle-user/${userId}`, { method: 'POST', body: JSON.stringify({ settlementType }) }),
  unsettleRefund: (id: string) =>
    fetchApi<void>(`/admin/training-refunds/${id}/unsettle`, { method: 'POST' }),

  // Archive of deleted slots
  getDeletedTrainingSlots: () =>
    fetchApi<DeletedTrainingSlot[]>(`/admin/training-slots/deleted`),
  searchUsers: (query: string) =>
    fetchApi<AdminUserSummary[]>(`/admin/users/search?query=${encodeURIComponent(query)}`),

  // Enrollments
  getEnrollmentsByEvent: (eventId: string) =>
    fetchApi<Enrollment[]>(`/admin/enrollments?eventId=${eventId}`),
  adminEnroll: (data: AdminEnrollRequest) =>
    fetchApi<Enrollment>('/admin/enrollments', { method: 'POST', body: JSON.stringify(data) }),
  deleteEnrollment: (id: string, notify = true) =>
    fetchApi<void>(`/admin/enrollments/${id}?notify=${notify}`, { method: 'DELETE' }),
  sendBulkEmail: (data: { eventId: string; message: string }) =>
    fetchApi<{ recipientCount: number }>('/admin/enrollments/bulk-email', { method: 'POST', body: JSON.stringify(data) }),

  // Users
  getUser: (id: string) =>
    fetchApi<AdminUserDetail>(`/admin/users/${id}`),
  getUsers: (params: { search?: string; page?: number; size?: number; sort?: string; direction?: 'asc' | 'desc' } = {}) => {
    const query = new URLSearchParams()
    if (params.search) query.set('search', params.search)
    query.set('page', String(params.page ?? 0))
    query.set('size', String(params.size ?? 50))
    query.set('sort', params.sort ?? 'created')
    query.set('direction', params.direction ?? 'desc')
    return fetchApi<PagedUsers>(`/admin/users?${query.toString()}`)
  },
  sendUserEmail: (data: SendUserEmailRequest) =>
    fetchApi<{ recipientCount: number }>('/admin/users/email', { method: 'POST', body: JSON.stringify(data) }),
  deleteUser: (id: string, notify = true) =>
    fetchApi<{ freedEnrollments: number; anonymizedEnrollments: number }>(`/admin/users/${id}?notify=${notify}`, { method: 'DELETE' }),
  forceLogout: (id: string) =>
    fetchApi<void>(`/admin/users/${id}/logout-all`, { method: 'POST' }),
  promoteUser: (id: string) =>
    fetchApi<AdminUser>(`/admin/users/${id}/promote`, { method: 'POST' }),
  demoteUser: (id: string) =>
    fetchApi<AdminUser>(`/admin/users/${id}/demote`, { method: 'POST' }),
}
