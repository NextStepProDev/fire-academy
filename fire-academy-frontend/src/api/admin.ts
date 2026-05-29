import { fetchApi } from './client'
import type { EventCategory, Instructor, EventType, EventInstance, Enrollment } from '../types'

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
  firstName: string
  lastName: string
  email: string
  phone: string
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
  uploadInstructorPhoto: (id: string, file: File) => {
    const formData = new FormData()
    formData.append('file', file)
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
  uploadEventTypeThumbnail: (id: string, file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return fetchApi<EventType>(`/admin/event-types/${id}/thumbnail`, { method: 'POST', body: formData })
  },
  addEventTypePhoto: (id: string, file: File) => {
    const formData = new FormData()
    formData.append('file', file)
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
  deleteEvent: (id: string) =>
    fetchApi<void>(`/admin/events/${id}`, { method: 'DELETE' }),
  toggleEventActive: (id: string) =>
    fetchApi<EventInstance>(`/admin/events/${id}/toggle-active`, { method: 'PATCH' }),

  // Enrollments
  getEnrollmentsByEvent: (eventId: string) =>
    fetchApi<Enrollment[]>(`/admin/enrollments?eventId=${eventId}`),
  getEnrollmentsByCategory: (category: EventCategory) =>
    fetchApi<Enrollment[]>(`/admin/enrollments/by-category?category=${category}`),
  adminEnroll: (data: AdminEnrollRequest) =>
    fetchApi<Enrollment>('/admin/enrollments', { method: 'POST', body: JSON.stringify(data) }),
  deleteEnrollment: (id: string) =>
    fetchApi<void>(`/admin/enrollments/${id}`, { method: 'DELETE' }),
}
