import { fetchApi } from './client'
import type { EventCategory, Instructor, EventType, EventInstance } from '../types'

export interface EnrollRequest {
  firstName: string
  lastName: string
  email: string
  phone: string
  note?: string
}

export const publicApi = {
  getInstructors: (category: EventCategory) =>
    fetchApi<Instructor[]>(`/public/instructors?category=${category}`),

  getEventTypes: (category: EventCategory) =>
    fetchApi<EventType[]>(`/public/event-types?category=${category}`),

  getUpcomingEvents: (category: EventCategory) =>
    fetchApi<EventInstance[]>(`/public/events?category=${category}`),

  getInstructor: (id: string) =>
    fetchApi<Instructor>(`/public/instructors/${id}`),

  getEventType: (id: string) =>
    fetchApi<EventType>(`/public/event-types/${id}`),

  getEvent: (id: string) =>
    fetchApi<EventInstance>(`/public/events/${id}`),

  enroll: (eventId: string, data: EnrollRequest) =>
    fetchApi<{ message: string }>(`/public/events/${eventId}/enroll`, {
      method: 'POST',
      body: JSON.stringify(data),
    }),
}
