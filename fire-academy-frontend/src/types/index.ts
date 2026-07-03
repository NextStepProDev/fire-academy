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

/** ISO: 1 = Monday … 7 = Sunday */
export type DayOfWeek = 1 | 2 | 3 | 4 | 5 | 6 | 7

/** Recurring training slot — admin view. */
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
  /** Date (ISO YYYY-MM-DD) from which the slot is deactivated; null = active. */
  deactivatedFrom: string | null
  /** For a deactivated slot: false once a cash refund was paid out (or credited surplus spent) → reactivation blocked. */
  reactivatable: boolean
  createdAt: string
}

/** Recurring training slot — public view (with available spots for the month). */
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
  /** Dates (ISO YYYY-MM-DD) of cancelled individual sessions in the selected month. */
  cancelledDates: string[]
}

/** Cancelled individual session of a slot (admin panel). */
export interface CancelledSession {
  id: string
  sessionDate: string
}

/** A participant affected by a cancelled session. `owedRefund` = had paid that month. */
export interface AffectedParticipant {
  firstName: string
  lastName: string
  email: string
  phone: string | null
  paid: boolean
  owedRefund: boolean
}

/** Club-wide cancelled session with the people it affected (admin overview, upcoming + archive). */
export interface CancelledSessionOverview {
  id: string
  slotId: string
  sessionDate: string
  eventTypeName: string
  instructorName: string | null
  dayOfWeek: number
  startTime: string
  endTime: string | null
  price: number | null
  /** Session date is today or later — it can still be restored. */
  future: boolean
  /** false when a cash refund was already paid out (or credited surplus spent) → restore is blocked. */
  restorable: boolean
  participants: AffectedParticipant[]
}

/** Whole-club day off (public schedule + admin panel). */
export interface TrainingHolidayItem {
  date: string
  label: string | null
}

/** Day off with id (admin management). */
export interface TrainingHoliday extends TrainingHolidayItem {
  id: string
  /** Paid participants who got the day-off email — >0 means removing it should warn to phone them. */
  notifiedCount: number
  /** false when a cash refund was already paid out (or credited surplus spent) → removal is blocked. */
  restorable: boolean
}

/** A refund owed (or settled) for a paid session that was cancelled — admin "Zwroty" view. */
export interface RefundEntry {
  id: string
  userId: string
  firstName: string
  lastName: string
  email: string
  phone: string
  trainingName: string
  sessionDate: string
  yearMonth: string
  amount: number
  type: 'HOLIDAY' | 'SESSION'
  label: string | null
  settledAt: string | null
  settlementType: 'REFUNDED' | 'CREDITED' | null
}

/** Deleted (archived) slot with data of former participants. */
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

/** One subscriber's whole-month bill across all their trainings (admin "Płatności miesięczne"). */
export interface UserMonthlyPayment {
  userId: string
  firstName: string
  lastName: string
  email: string
  phone: string
  trainings: MonthlyTrainingLine[]
  totalAmount: number
  allPaid: boolean
  /** When the whole month was marked paid (ISO instant); null if nothing paid yet. */
  paidAt: string | null
  creditBalance: number
}

export interface MonthlyTrainingLine {
  trainingName: string
  dayOfWeek: number
  startTime: string
  endTime: string | null
  amount: number
  paid: boolean
}

export interface ArchivedParticipant {
  firstName: string
  lastName: string
  email: string
  phone: string
  startMonth: string
  endMonth: string | null
}

/** Registered user — admin search result. */
export interface AdminUserSummary {
  id: string
  firstName: string
  lastName: string
  email: string
}

/** Admin roster entry — one enrolled participant for a given month. */
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
  /** Surplus (credited refunds) still available to discount this subscriber's upcoming bills. */
  creditBalance: number
}

/** Logged-in user's subscription to a slot — account view. */
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
  /** NET amount after subtracting surplus credit. */
  monthlyAmount: number | null
  /** Surplus (credited refund) applied to this month, 0 if none. */
  monthlyCreditApplied: number
  /** Whether the organizer has marked the billing month as paid. */
  billingMonthPaid: boolean
  /** What the client actually paid for the billing month (real amount even if later cut by a cancellation). */
  billingMonthPaidAmount: number | null
  /** Money owed for cancelled paid sessions not yet resolved — claim as refund or credit toward a future month. */
  pendingRefundAmount: number
  /** Surplus (credited refunds) waiting to reduce upcoming bills — visible year-round, not only in the estimate window. */
  upcomingCreditBalance: number
  /** Estimated billing for next month — only set within ~7 days before it starts, else null. */
  nextBillingMonth: string | null
  nextMonthSessions: number | null
  nextMonthAmount: number | null
  nextMonthCreditApplied: number | null
  /** Upcoming cancelled sessions of this slot (ISO YYYY-MM-DD). */
  cancelledDates: string[]
  /** Upcoming days off landing on this slot's weekday (ISO YYYY-MM-DD). */
  holidayDates: string[]
  /** Set when the whole training was scheduled to stop from this date — no sessions/bill after it. */
  slotDeactivatedFrom: string | null
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
