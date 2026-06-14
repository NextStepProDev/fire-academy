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

/** Liczba wystąpień danego dnia tygodnia (ISO 1–7) w miesiącu 'YYYY-MM'. */
export function monthlyOccurrences(dayOfWeek: number, month: string): number {
  const [year, m] = month.split('-').map(Number)
  const targetJsDay = dayOfWeek === 7 ? 0 : dayOfWeek // JS: 0 = niedziela
  const daysInMonth = new Date(year, m, 0).getDate()
  let count = 0
  for (let day = 1; day <= daysInMonth; day++) {
    if (new Date(year, m - 1, day).getDay() === targetJsDay) count++
  }
  return count
}

/** Formatuje 'YYYY-MM' na polską nazwę, np. 'czerwiec 2026'. */
export function formatMonth(month: string): string {
  const [year, m] = month.split('-').map(Number)
  return new Date(year, m - 1, 1).toLocaleDateString('pl', { month: 'long', year: 'numeric' })
}
