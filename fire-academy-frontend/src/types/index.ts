export interface User {
  id: string
  email: string
  firstName: string
  lastName: string
  phone: string
  role: 'USER' | 'ADMIN'
  isAdmin: boolean
  emailNotificationsEnabled: boolean
  preferredLanguage: string
  hasPassword: boolean
  createdAt: string
}

export type EventCategory = 'CAMP' | 'COURSE' | 'TRAINING'

export interface Instructor {
  id: string
  firstName: string
  lastName: string
  bio: string | null
  photoUrl: string | null
  categories: EventCategory[]
  displayOrder: number
  active: boolean
  createdAt: string
}

export interface EventTypePhoto {
  id: string
  url: string
  displayOrder: number
}

export interface EventType {
  id: string
  category: EventCategory
  name: string
  description: string | null
  price: number | null
  maxParticipants: number | null
  duration: string | null
  thumbnailUrl: string | null
  photos: EventTypePhoto[]
  displayOrder: number
  active: boolean
  createdAt: string
}

export interface EventInstance {
  id: string
  eventTypeId: string
  eventTypeName: string
  startDate: string
  endDate: string | null
  startTime: string | null
  location: string | null
  price: number | null
  maxParticipants: number | null
  availableSpots: number
  active: boolean
  createdAt: string
}

export interface Enrollment {
  id: string
  eventId: string
  eventTypeName: string
  eventStartDate: string
  firstName: string
  lastName: string
  email: string
  phone: string
  addedByAdmin: boolean
  createdAt: string
}
