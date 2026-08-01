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
  /** 1-on-1 coaching client — unlocks the personal training calendar. Set by an admin. */
  isAthlete: boolean
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
  isAthlete: boolean
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
  isAthlete: boolean
  preferredLanguage: string
  hasPassword: boolean
  oauthLinked: boolean
  avatarUrl: string | null
  createdAt: string
  currentEnrollments: UserEnrollment[]
  pastEnrollments: UserEnrollment[]
}

/** One row of the coach's 1-on-1 roster. */
export interface AthleteSummary {
  id: string
  firstName: string
  lastName: string
  email: string
  avatarUrl: string | null
  unreadCount: number
}

/** Computed server-side, never stored — the frontend only colours it. */
export type TrainingStatus = 'PLANNED' | 'COMPLETED' | 'MISSED'

export interface PersonalTraining {
  id: string
  date: string
  /** Null is the normal case — an untimed training means "do this that day". */
  startTime: string | null
  endTime: string | null
  title: string
  description: string | null
  status: TrainingStatus
  completedAt: string | null
  feedback: string | null
  rpe: number | null
  createdByAdmin: boolean
  lastModifiedByAdmin: boolean
  /** The other side changed this since we last looked — drives the dot on the card. */
  unread: boolean
  commentCount: number
  attachments: Attachment[]
  /** Echoed back on update so a concurrent edit is caught instead of silently overwritten. */
  version: number
  createdAt: string
  updatedAt: string
}

export interface DeletedTrainingNotice {
  id: string
  date: string
  startTime: string | null
  title: string
  deletedAt: string
}

/**
 * One occurrence of a recurring group slot. Deliberately has no id: it is not a row anywhere, it is
 * computed from the subscription on every request. Nothing in the UI may offer to edit it.
 */
export interface RecurringSession {
  date: string
  slotId: string
  name: string
  instructorName: string | null
  startTime: string
  endTime: string | null
}

export interface CalendarRange {
  from: string
  to: string
  trainings: PersonalTraining[]
  recurring: RecurringSession[]
  deletions: DeletedTrainingNotice[]
}

export interface TrainingComment {
  id: string
  body: string
  fromCoach: boolean
  authorName: string | null
  createdAt: string
}

export interface MyTrainingSummary {
  unreadCount: number
  deletedCount: number
  nextTrainingDate: string | null
}

export interface CreateTrainingBody {
  date: string
  startTime?: string | null
  endTime?: string | null
  title: string
  description?: string | null
  /** undefined = leave materials alone · [] = clear · list = replace. */
  attachments?: AttachmentInput[] | null
}

export interface UpdateTrainingBody extends CreateTrainingBody {
  version: number
}

export type PasteMode = 'COPY' | 'MOVE'

export interface WeightPoint {
  date: string
  weightKg: number
  /** Trailing 7-day average ending on this day — computed server-side, one definition of "trend". */
  trendKg: number | null
}

export interface WeightSeries {
  points: WeightPoint[]
  currentTrendKg: number | null
  /** Negative when losing. Compares two trend values a week apart, not two readings. */
  weeklyChangePercent: number | null
  /** Absent from the client's response entirely — coach-only, like the overtraining signal. */
  rapidLoss?: boolean
  /** How many mornings of the 7-day window the trend rests on. */
  trendReadings: number
  /** Below this many readings a weight goal will not close itself. Sent, never assumed here. */
  minReadingsToCloseGoal: number
}

export interface TrainingStats {
  thisMonthCount: number
  prevMonthCount: number
  totalCount: number
  firstActivityDate: string | null
  currentStreakWeeks: number
  bestStreakWeeks: number
  avgPerMonth: number | null
  /** Non-zero days only, keyed 'YYYY-MM-DD'. */
  heatmap: Record<string, number>
  byType: { personal: number; recurring: number }
  /** Null when nothing was planned — 0% would claim a failure that never happened. */
  attendancePercent: number | null
  avgRpeOverall: number | null
  avgRpeRecent: number | null
  rpeDistribution: { light: number; medium: number; hard: number }
  /** Absent from the client's response entirely — this signal is for the coach. */
  overtraining?: boolean
}

export type GoalHorizon = 'SHORT' | 'MEDIUM' | 'LONG'

export type GoalKind = 'GENERAL' | 'WEIGHT'

export interface AthleteGoal {
  id: string
  kind: GoalKind
  horizon: GoalHorizon
  content: string
  targetDate: string | null
  achievedAt: string | null
  /** Closed by the weight log rather than a person — the only kind the coach may reopen. */
  achievedAutomatically: boolean
  targetWeightKg: number | null
  /** The trend when the goal was set; the progress bar measures from it. */
  startWeightKg: number | null
}

export interface AthleteGoals {
  active: AthleteGoal[]
  achieved: AthleteGoal[]
}

export interface GoalInput {
  horizon: GoalHorizon
  content: string
  targetDate?: string | null
  /** Present makes this a weight goal; absent makes it a general one. */
  targetWeightKg?: number | null
}

export type AttachmentKind = 'LINK' | 'VIDEO'

export interface Attachment {
  id: string
  kind: AttachmentKind
  label: string | null
  url: string | null
  videoId: string | null
  videoName: string | null
  /** Canonical player URL, built server-side from the video id. */
  embedUrl: string | null
  thumbnailUrl: string | null
}

export interface AttachmentInput {
  kind: AttachmentKind
  label?: string | null
  url?: string | null
  videoId?: string | null
}

export interface ExerciseVideo {
  id: string
  name: string
  url: string
  description: string | null
  category: string | null
  embedUrl: string
  thumbnailUrl: string
  archived: boolean
}

export interface PagedExerciseVideos {
  content: ExerciseVideo[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface ExerciseVideoInput {
  name: string
  url: string
  description?: string | null
  category?: string | null
}

export interface TrainingTemplate {
  id: string
  title: string
  description: string | null
  defaultDurationMinutes: number | null
  attachments: Attachment[]
}

export interface TrainingTemplateInput {
  title: string
  description?: string | null
  defaultDurationMinutes?: number | null
  attachments?: AttachmentInput[] | null
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
  settlementType: SettlementType | null
}

/** How a refund was resolved: cash back, credited toward a month, or made up in another group (nothing owed). */
export type SettlementType = 'REFUNDED' | 'CREDITED' | 'MADE_UP'

/** An ended subscription still sitting on unconsumed CREDITED surplus — admin "Zwroty" view. */
export interface UnconsumedCreditEntry {
  enrollmentId: string
  userId: string
  firstName: string
  lastName: string
  email: string
  phone: string
  trainingName: string
  endMonth: string
  balance: number
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
  /** Paid individually via the per-slot roster toggle → a whole-month revert leaves it alone. */
  pinned: boolean
  /** Unpaid and the month's first session already passed — a reserved spot that was never paid for. */
  overdue: boolean
  enrollmentId: string
  /** Subscription's start month (YYYY-MM) — the "count from" date is only editable in this month. */
  startMonth: string
  /** Organizer's explicit first-month start override (ISO date), or null when billing runs from signup. */
  billableFrom: string | null
  /** First attendable session of a partial first month (ISO date); null for a whole from-day-1 month. Stays set
   *  after payment, so the roster can show from which day a paid month is actually valid. */
  validFrom: string | null
  /** Slot / type / trainer, so the participants overview can filter by them. */
  slotId: string
  eventTypeId: string
  instructorId: string | null
  instructorName: string | null
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
  /** Organizer's explicit first-month start override (ISO date), or null when billing runs from signup. */
  billableFrom: string | null
  /** Live NET amount owed for the viewed month (frozen once paid). */
  amount: number
  /** Unpaid and the month's first session already passed — a reserved spot that was never paid for. */
  overdue: boolean
}

/** One client's training-focused profile (admin), opened from the trainings section. */
export interface TrainingUserSubscription {
  enrollmentId: string
  trainingName: string
  instructorName: string | null
  dayOfWeek: number
  startTime: string
  endTime: string | null
  price: number | null
  startMonth: string
  endMonth: string | null
  billableFrom: string | null
  enrolledAt: string
  active: boolean
}

export interface TrainingUserPayment {
  trainingName: string
  yearMonth: string
  amount: number | null
  creditApplied: number
  pinned: boolean
  paidAt: string
}

export interface TrainingUserRefund {
  trainingName: string
  sessionDate: string
  amount: number
  type: string
  label: string | null
  owedSince: string
  settledAt: string | null
  settlementType: SettlementType | null
  /** For a CREDITED surplus: the month whose paid bill it discounted (YYYY-MM), or null if not yet consumed. */
  consumedInMonth: string | null
}

export interface TrainingUserHistory {
  userId: string
  firstName: string
  lastName: string
  email: string
  phone: string | null
  joinedAt: string
  creditBalance: number
  subscriptions: TrainingUserSubscription[]
  payments: TrainingUserPayment[]
  refunds: TrainingUserRefund[]
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
