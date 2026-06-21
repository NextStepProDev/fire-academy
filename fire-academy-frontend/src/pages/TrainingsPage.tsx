import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { CalendarCheck, LogIn } from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import { EventsPage } from './EventsPage'

export function TrainingsPage() {
  const { t } = useTranslation('account')
  const { isAuthenticated, isAdmin, user } = useAuth()

  // Personalized banner — login is tied to trainings. The training list itself
  // (events, event types, instructors) stays fully public in <EventsPage />.
  // We don't show the user banner to an admin (they have their own panel).
  const banner = isAdmin ? null : isAuthenticated ? (
    <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-surface-800 bg-surface-900 px-4 py-3">
      <p className="text-surface-200 text-sm">{t('banner.loggedIn', { name: user?.firstName })}</p>
      <Link
        to="/moje-konto"
        className="inline-flex items-center gap-2 rounded-lg bg-primary-600 px-3 py-1.5 text-sm font-semibold text-white hover:bg-primary-700 transition-colors"
      >
        <CalendarCheck className="w-4 h-4" />
        {t('banner.myReservations')}
      </Link>
    </div>
  ) : (
    <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-surface-800 bg-surface-900 px-4 py-3">
      <p className="text-surface-300 text-sm">{t('banner.guest')}</p>
      <Link
        to="/logowanie"
        className="inline-flex items-center gap-2 rounded-lg bg-primary-600 px-3 py-1.5 text-sm font-semibold text-white hover:bg-primary-700 transition-colors"
      >
        <LogIn className="w-4 h-4" />
        {t('banner.login')}
      </Link>
    </div>
  )

  return (
    <>
      {banner && (
        <div className="max-w-6xl mx-auto px-4 pt-10 -mb-6">{banner}</div>
      )}
      <EventsPage category="TRAINING" />
    </>
  )
}
