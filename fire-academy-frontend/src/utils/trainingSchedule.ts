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

/**
 * Number of sessions to be paid for in a given month:
 * for the current month counted from TODAY to the end (remaining), for future months — all of them.
 */
export function remainingOccurrences(dayOfWeek: number, month: string): number {
  const now = new Date()
  const [year, m] = month.split('-').map(Number)
  const isCurrent = year === now.getFullYear() && m === now.getMonth() + 1
  const targetJsDay = dayOfWeek === 7 ? 0 : dayOfWeek
  const daysInMonth = new Date(year, m, 0).getDate()
  const fromDay = isCurrent ? now.getDate() : 1
  let count = 0
  for (let day = fromDay; day <= daysInMonth; day++) {
    if (new Date(year, m - 1, day).getDay() === targetJsDay) count++
  }
  return count
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
