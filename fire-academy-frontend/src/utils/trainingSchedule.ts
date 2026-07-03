// Utilities for recurring training slots.
// Months in 'YYYY-MM' format. Day of week in ISO: 1 = Monday … 7 = Sunday.

/** How many months ahead (beyond the current one) can be booked — consistent with the backend. */
export const BOOKABLE_MONTHS_AHEAD = 2

/** Current month as 'YYYY-MM'. */
export function currentMonth(): string {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

/** List of months available for browsing/booking (current + BOOKABLE_MONTHS_AHEAD). */
export function visibleMonths(): string[] {
  const now = new Date()
  const result: string[] = []
  for (let i = 0; i <= BOOKABLE_MONTHS_AHEAD; i++) {
    const d = new Date(now.getFullYear(), now.getMonth() + i, 1)
    result.push(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`)
  }
  return result
}

/** How many past months the admin can browse — to cancel historical sessions that did not take place. */
export const ADMIN_PAST_MONTHS = 2

/** Admin trainings view: a couple of past months (to fix history) + the bookable ones. Current is the default. */
export function adminVisibleMonths(): string[] {
  const cur = currentMonth()
  const past: string[] = []
  for (let i = ADMIN_PAST_MONTHS; i >= 1; i--) past.push(addMonths(cur, -i))
  return [...past, ...visibleMonths()]
}

/**
 * Number of sessions to be paid for in a given month:
 * for the current month counted from TODAY to the end (remaining), for future months — all of them.
 * `closedDates` (ISO YYYY-MM-DD) are dates the slot does NOT take place — days off + cancelled sessions.
 */
export function remainingOccurrences(dayOfWeek: number, month: string, closedDates: string[] = []): number {
  const now = new Date()
  const [year, m] = month.split('-').map(Number)
  const isCurrent = year === now.getFullYear() && m === now.getMonth() + 1
  const targetJsDay = dayOfWeek === 7 ? 0 : dayOfWeek
  const daysInMonth = new Date(year, m, 0).getDate()
  const fromDay = isCurrent ? now.getDate() : 1
  const closed = new Set(closedDates)
  let count = 0
  for (let day = fromDay; day <= daysInMonth; day++) {
    const iso = `${month}-${String(day).padStart(2, '0')}`
    if (new Date(year, m - 1, day).getDay() === targetJsDay && !closed.has(iso)) count++
  }
  return count
}

/** Days off (ISO) for the given month that land on the slot's weekday — i.e. that close this slot. */
export function holidaysForDay(holidays: { date: string }[], dayOfWeek: number): string[] {
  const targetJsDay = dayOfWeek === 7 ? 0 : dayOfWeek
  return holidays
    .filter(h => {
      const [y, m, d] = h.date.split('-').map(Number)
      return new Date(y, m - 1, d).getDay() === targetJsDay
    })
    .map(h => h.date)
}

/** Formats 'YYYY-MM' into a Polish name, e.g. 'czerwiec 2026'. */
export function formatMonth(month: string): string {
  const [year, m] = month.split('-').map(Number)
  return new Date(year, m - 1, 1).toLocaleDateString('pl', { month: 'long', year: 'numeric' })
}

/** Shifts month 'YYYY-MM' by n months. */
export function addMonths(month: string, n: number): string {
  const [year, m] = month.split('-').map(Number)
  const d = new Date(year, m - 1 + n, 1)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}
