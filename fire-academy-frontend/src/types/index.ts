export interface User {
  id: string
  email: string
  firstName: string
  lastName: string
  phone: string
  role: 'USER' | 'ADMIN'
  isAdmin: boolean
  superAdmin: boolean
  privacyAccepted: boolean
  marketingConsent: boolean
  preferredLanguage: string
  hasPassword: boolean
  avatarUrl: string | null
  createdAt: string
}

export interface AdminUser {
  id: string
  email: string
  firstName: string
  lastName: string
  phone: string | null
  role: 'USER' | 'ADMIN'
  isAdmin: boolean
  superAdmin: boolean
  emailVerified: boolean
  marketingConsent: boolean
  createdAt: string
}

export interface PagedUsers {
  content: AdminUser[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface UserEnrollment {
  id: string
  eventId: string
  eventName: string
  category: EventCategory
  startDate: string
  endDate: string | null
  startTime: string | null
  endTime: string | null
  location: string | null
  note: string | null
  addedByAdmin: boolean
  past: boolean
  createdAt: string
}

// A logged-in user's own enrollment ("My reservations"). Participant data comes from the account.
export interface MyEnrollment {
  id: string
  eventId: string
  eventName: string
  category: EventCategory
  startDate: string
  endDate: string | null
  startTime: string | null
  endTime: string | null
  location: string | null
  note: string | null
  past: boolean
  canCancel: boolean
  createdAt: string
}

export interface MyEnrollments {
  current: MyEnrollment[]
  past: MyEnrollment[]
}

export interface AdminUserDetail {
  id: string
  email: string
  firstName: string
  lastName: string
  phone: string | null
  role: 'USER' | 'ADMIN'
  isAdmin: boolean
  superAdmin: boolean
  emailVerified: boolean
  marketingConsent: boolean
  preferredLanguage: string
  hasPassword: boolean
  oauthLinked: boolean
  avatarUrl: string | null
  createdAt: string
  currentEnrollments: UserEnrollment[]
  pastEnrollments: UserEnrollment[]
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
  thumbnailUrl: string | null
  photos: EventTypePhoto[]
  displayOrder: number
  active: boolean
  createdAt: string
}

export interface EventInstance {
  id: string
  eventTypeId: string | null
  eventTypeName: string
  description: string | null
  startDate: string
  endDate: string | null
  startTime: string | null
  endTime: string | null
  location: string | null
  price: number | null
  maxParticipants: number | null
  availableSpots: number
  enrollmentCount: number
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
  phone: string | null
  note: string | null
  addedByAdmin: boolean
  createdAt: string
}
