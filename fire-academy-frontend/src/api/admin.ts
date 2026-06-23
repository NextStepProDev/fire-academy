import { fetchApi } from './client'
import type { EventCategory, Instructor, EventType, EventInstance, Enrollment, AdminUser, PagedUsers, AdminUserDetail } from '../types'
import { validateImageFile, compressImage } from '../utils/imageUtils'

export type EmailAudience = 'MARKETING' | 'ALL' | 'SELECTED'

interface SendUserEmailRequest {
  subject: string
  message: string
  audience: EmailAudience
  userIds?: string[]
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
