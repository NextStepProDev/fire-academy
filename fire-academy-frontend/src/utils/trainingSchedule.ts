// Narzędzia do cyklicznych slotów treningowych.
// Miesiące w formacie 'YYYY-MM'. Dzień tygodnia w ISO: 1 = poniedziałek … 7 = niedziela.

/** Ile miesięcy do przodu (poza bieżącym) można rezerwować — spójne z backendem. */
export const BOOKABLE_MONTHS_AHEAD = 2

/** Bieżący miesiąc jako 'YYYY-MM'. */
export function currentMonth(): string {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

/** Lista miesięcy dostępnych do przeglądania/rezerwacji (bieżący + BOOKABLE_MONTHS_AHEAD). */
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
 * Liczba zajęć do opłacenia w danym miesiącu:
 * dla bieżącego miesiąca liczone od DZIŚ do końca (pozostałe), dla przyszłych — wszystkie.
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

/** Formatuje 'YYYY-MM' na polską nazwę, np. 'czerwiec 2026'. */
export function formatMonth(month: string): string {
  const [year, m] = month.split('-').map(Number)
  return new Date(year, m - 1, 1).toLocaleDateString('pl', { month: 'long', year: 'numeric' })
}

/** Przesuwa miesiąc 'YYYY-MM' o n miesięcy. */
export function addMonths(month: string, n: number): string {
  const [year, m] = month.split('-').map(Number)
  const d = new Date(year, m - 1 + n, 1)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}
