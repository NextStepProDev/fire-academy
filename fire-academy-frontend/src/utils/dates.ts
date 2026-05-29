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
