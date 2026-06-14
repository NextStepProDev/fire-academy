import { fetchApi } from './client'
import type { EventCategory, Instructor, EventType, EventInstance, TrainingSlotCard } from '../types'

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

  getTrainingSlots: (month: string) =>
    fetchApi<TrainingSlotCard[]>(`/public/training-slots?month=${month}`),
}
