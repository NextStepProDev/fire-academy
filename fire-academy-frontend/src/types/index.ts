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

/** ISO: 1 = poniedziałek … 7 = niedziela */
export type DayOfWeek = 1 | 2 | 3 | 4 | 5 | 6 | 7

/** Cykliczny slot treningowy — widok admina. */
export interface TrainingSlot {
  id: string
  eventTypeId: string
  eventTypeName: string
  instructorId: string | null
  instructorName: string | null
  dayOfWeek: number
  startTime: string
  endTime: string | null
  price: number | null
  maxParticipants: number
  displayOrder: number
  enrolledThisMonth: number
  active: boolean
  /** Data (ISO YYYY-MM-DD), od której slot jest zdezaktywowany; null = aktywny. */
  deactivatedFrom: string | null
  createdAt: string
}

/** Cykliczny slot treningowy — widok publiczny (z wolnymi miejscami dla miesiąca). */
export interface TrainingSlotCard {
  id: string
  eventTypeId: string
  eventTypeName: string
  instructorId: string | null
  instructorName: string | null
  dayOfWeek: number
  startTime: string
  endTime: string | null
  price: number | null
  maxParticipants: number
  availableSpots: number
  /** Daty (ISO YYYY-MM-DD) odwołanych pojedynczych zajęć w wybranym miesiącu. */
  cancelledDates: string[]
}

/** Odwołane pojedyncze zajęcia slotu (panel admina). */
export interface CancelledSession {
  id: string
  sessionDate: string
}

/** Usunięty (zarchiwizowany) slot z danymi byłych uczestników. */
export interface DeletedTrainingSlot {
  id: string
  eventTypeName: string
  instructorName: string | null
  dayOfWeek: number
  startTime: string
  endTime: string | null
  deletedAt: string
  participants: ArchivedParticipant[]
}

export interface ArchivedParticipant {
  firstName: string
  lastName: string
  email: string
  phone: string
  startMonth: string
  endMonth: string | null
}

/** Zarejestrowany użytkownik — wynik wyszukiwarki admina. */
export interface AdminUserSummary {
  id: string
  firstName: string
  lastName: string
  email: string
}

/** Pozycja rostera admina — jeden zapisany uczestnik na dany miesiąc. */
export interface TrainingRosterEntry {
  enrollmentId: string
  userId: string
  firstName: string
  lastName: string
  email: string
  phone: string
  startMonth: string
  endMonth: string | null
  indefinite: boolean
  paid: boolean
}

/** Subskrypcja zalogowanego użytkownika na slot — widok konta. */
export interface MyTrainingEnrollment {
  id: string
  slotId: string
  eventTypeId: string
  eventTypeName: string
  instructorName: string | null
  dayOfWeek: number
  startTime: string
  endTime: string | null
  price: number | null
  startMonth: string
  endMonth: string | null
  billingMonth: string
  sessionsInBillingMonth: number
  monthlyAmount: number | null
  /** Nadchodzące odwołane zajęcia tego slotu (ISO YYYY-MM-DD). */
  cancelledDates: string[]
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
