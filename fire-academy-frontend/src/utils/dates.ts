import { format, parseISO } from 'date-fns'
import { pl } from 'date-fns/locale'

export function formatDate(iso: string): string {
  return format(parseISO(iso), 'd MMM yyyy', { locale: pl })
}

export function formatDateRange(start: string, end: string | null): string {
  const startDate = parseISO(start)
  if (!end) return format(startDate, 'd MMM yyyy', { locale: pl })

  const endDate = parseISO(end)
  const sameMonth = startDate.getMonth() === endDate.getMonth() && startDate.getFullYear() === endDate.getFullYear()

  if (sameMonth) {
    return `${format(startDate, 'd', { locale: pl })} – ${format(endDate, 'd MMM yyyy', { locale: pl })}`
  }
  return `${format(startDate, 'd MMM', { locale: pl })} – ${format(endDate, 'd MMM yyyy', { locale: pl })}`
}

/**
 * Formatuje termin jako jeden ciągły blok „od początku do końca".
 * Wielodniowy z godzinami: godzina przyklejona do swojej daty (start pierwszego dnia → koniec
 * ostatniego), żeby nie sugerować „codziennie w tych godzinach", np.
 * „15 lip 2026, 09:00 – 18 lip 2026, 16:00". Jednodniowy: „30 maj 2026, 10:00 – 11:30".
 */
export function formatSchedule(
  startDate: string,
  endDate: string | null,
  startTime: string | null,
  endTime: string | null,
): string {
  const multiDay = !!endDate && endDate !== startDate

  if (multiDay) {
    if (startTime || endTime) {
      const from = `${formatDate(startDate)}${startTime ? `, ${startTime}` : ''}`
      const to = `${formatDate(endDate)}${endTime ? `, ${endTime}` : ''}`
      return `${from} – ${to}`
    }
    return formatDateRange(startDate, endDate)
  }

  const day = formatDate(startDate)
  if (startTime && endTime) return `${day}, ${startTime} – ${endTime}`
  if (startTime) return `${day}, ${startTime}`
  return day
}
