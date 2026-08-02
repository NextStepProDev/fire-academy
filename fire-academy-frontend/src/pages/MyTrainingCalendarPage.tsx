import { useTranslation } from 'react-i18next'
import { Link, Navigate } from 'react-router-dom'
import { ArrowLeft } from 'lucide-react'
import { Seo } from '../components/seo/Seo'
import { useAuth } from '../context/AuthContext'
import { TrainingCalendar } from '../components/training-calendar/TrainingCalendar'
import { TrainingConsentGate } from '../components/training-calendar/TrainingConsentGate'
import { athleteAdapter } from '../components/training-calendar/adapter'
import { GoalsBoard } from '../components/goals/GoalsBoard'
import { TrainingStatsPanel } from '../components/training-stats/TrainingStatsPanel'
import { WeightPanel } from '../components/weight/WeightPanel'

export function MyTrainingCalendarPage() {
  const { t } = useTranslation('calendar')
  const { user, refreshUser } = useAuth()

  // The API answers 404 for a non-client anyway; bouncing here avoids showing an error page to
  // someone who simply followed a stale link.
  if (!user?.isAthlete) {
    return <Navigate to="/moje-konto" replace />
  }

  return (
    <div className="mx-auto max-w-6xl px-4 py-10 space-y-6">
      <Seo title={t('my.title')} path="/moje-konto/plan-treningowy" />

      <Link to="/moje-konto"
        className="inline-flex items-center gap-2 text-sm text-surface-400 transition-colors hover:text-primary-400">
        <ArrowLeft className="h-4 w-4" />
        {t('my.back')}
      </Link>

      <div>
        <h1 className="text-2xl font-bold text-surface-100">{t('my.title')}</h1>
        <p className="mt-1 text-sm text-surface-400">{t('my.subtitle')}</p>
      </div>

      {/* Consent first: nothing below may fetch before it (the API 409s), and clients who predate
          the consent screen pass through it once, here. */}
      {!user.trainingConsent ? (
        <TrainingConsentGate onAccepted={refreshUser} />
      ) : (
        <>
          {/* Read-only for the client: goals are the coach's call. */}
          <GoalsBoard athleteId={null} />

          <TrainingCalendar adapter={athleteAdapter(user.id)} />

          <WeightPanel athleteId={null} />

          <TrainingStatsPanel athleteId={null} />

          {/* Said once, quietly, at the foot of the page it covers — everything here is shared with
              the coach, and the client should have read that at least once without being told
              repeatedly. */}
          <p className="border-t border-surface-800 pt-4 text-xs text-surface-500">
            {t('my.sharedNotice')}
          </p>
        </>
      )}
    </div>
  )
}
