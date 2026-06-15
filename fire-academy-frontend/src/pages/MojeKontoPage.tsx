import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { CalendarCheck, CalendarOff, Clock, Pencil, User as UserIcon, MapPin, Calendar, X, Trash2 } from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import { userApi } from '../api/client'
import { userApi as trainingApi } from '../api/user'
import { Seo } from '../components/seo/Seo'
import { Button } from '../components/ui/Button'
import { Avatar } from '../components/ui/Avatar'
import { ConfirmDialog } from '../components/ui/ConfirmDialog'
import { LoadingSpinner } from '../components/ui/LoadingSpinner'
import { useToast } from '../context/ToastContext'
import { formatSchedule } from '../utils/dates'
import { categoryToSlug } from '../utils/categorySlug'
import { formatMonth, currentMonth } from '../utils/trainingSchedule'
import type { MyEnrollment } from '../types'

export function MojeKontoPage() {
  const { t } = useTranslation('account')
  const { user } = useAuth()
  const { showToast } = useToast()
  const queryClient = useQueryClient()
  const [cancelError, setCancelError] = useState<string | null>(null)
  const [cancelTrainingId, setCancelTrainingId] = useState<string | null>(null)

  const enrollmentsQuery = useQuery({
    queryKey: ['user', 'enrollments'],
    queryFn: () => userApi.getMyEnrollments(),
  })

  const cancelMutation = useMutation({
    mutationFn: (id: string) => userApi.cancelEnrollment(id),
    onSuccess: () => {
      setCancelError(null)
      queryClient.invalidateQueries({ queryKey: ['user', 'enrollments'] })
    },
    onError: (err) => setCancelError(err instanceof Error ? err.message : t('reservations.cancelError')),
  })

  const handleCancel = (id: string) => {
    if (window.confirm(t('reservations.cancelConfirm'))) {
      cancelMutation.mutate(id)
    }
  }

  const trainingsQuery = useQuery({
    queryKey: ['user', 'training-enrollments'],
    queryFn: trainingApi.getMyTrainingEnrollments,
  })

  const cancelTrainingMut = useMutation({
    mutationFn: trainingApi.cancelTrainingEnrollment,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['user', 'training-enrollments'] })
      queryClient.invalidateQueries({ queryKey: ['public', 'training-slots'] })
      showToast(t('trainings.cancelSuccess'), 'success')
    },
    onError: (e: Error) => showToast(e.message, 'error'),
  })

  if (!user) return null

  const current = enrollmentsQuery.data?.current ?? []
  const past = enrollmentsQuery.data?.past ?? []
  const isEmpty = current.length === 0 && past.length === 0

  const trainings = trainingsQuery.data ?? []
  const cm = currentMonth()
  const monthTotal = trainings
    .filter(e => e.billingMonth === cm && e.monthlyAmount != null)
    .reduce((sum, e) => sum + (e.monthlyAmount ?? 0), 0)

  const renderRow = (e: MyEnrollment) => (
    <div key={e.id} className="bg-surface-800/50 rounded-lg p-4 border border-surface-800">
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <Link
            to={`/${categoryToSlug(e.category)}/termin/${e.eventId}`}
            className="text-surface-100 font-medium hover:text-primary-400 transition-colors"
          >
            {e.eventName}
          </Link>
          <p className="text-sm text-surface-400 mt-1 flex items-center gap-1.5">
            <Calendar className="w-4 h-4 shrink-0" />
            {formatSchedule(e.startDate, e.endDate, e.startTime, e.endTime)}
          </p>
          {e.location && (
            <p className="text-sm text-surface-400 mt-0.5 flex items-center gap-1.5">
              <MapPin className="w-4 h-4 shrink-0" />
              {e.location}
            </p>
          )}
          {e.note && (
            <p className="text-xs text-surface-500 mt-2">
              <span className="text-surface-400">{t('reservations.noteLabel')}</span> {e.note}
            </p>
          )}
        </div>
        {e.canCancel && (
          <Button
            variant="secondary"
            className="!px-2.5 !py-1.5 text-sm shrink-0"
            onClick={() => handleCancel(e.id)}
            loading={cancelMutation.isPending && cancelMutation.variables === e.id}
          >
            <X className="w-4 h-4 mr-1" />
            {t('reservations.cancel')}
          </Button>
        )}
      </div>
    </div>
  )

  return (
    <div className="max-w-3xl mx-auto px-4 py-10 space-y-8">
      <Seo title={t('title')} path="/moje-konto" />

      <div className="flex items-center gap-5">
        <Avatar
          src={user.avatarUrl}
          name={user.firstName}
          className="w-20 h-20"
          textClassName="text-2xl"
        />
        <div>
          <h1 className="text-3xl md:text-4xl font-bold text-surface-100">{t('title')}</h1>
          <p className="text-surface-400 mt-2">{t('greeting', { name: user.firstName })}</p>
        </div>
      </div>

      {/* Profile */}
      <section className="bg-surface-900 rounded-xl p-6 border border-surface-800">
        <div className="flex items-center justify-between mb-5">
          <h2 className="flex items-center gap-2 text-xl font-bold text-surface-100">
            <UserIcon className="w-5 h-5 text-primary-400" />
            {t('profile.title')}
          </h2>
          <Link to="/settings">
            <Button variant="secondary" className="!px-3 !py-1.5 text-sm">
              <Pencil className="w-4 h-4 mr-1.5" />
              {t('profile.edit')}
            </Button>
          </Link>
        </div>

        <dl className="grid grid-cols-1 sm:grid-cols-2 gap-x-8 gap-y-4">
          <div>
            <dt className="text-xs uppercase tracking-wide text-surface-500">{t('profile.firstName')}</dt>
            <dd className="text-surface-100 mt-0.5">{user.firstName}</dd>
          </div>
          <div>
            <dt className="text-xs uppercase tracking-wide text-surface-500">{t('profile.lastName')}</dt>
            <dd className="text-surface-100 mt-0.5">{user.lastName}</dd>
          </div>
          <div>
            <dt className="text-xs uppercase tracking-wide text-surface-500">{t('profile.email')}</dt>
            <dd className="text-surface-100 mt-0.5 break-all">{user.email}</dd>
          </div>
          <div>
            <dt className="text-xs uppercase tracking-wide text-surface-500">{t('profile.phone')}</dt>
            <dd className="text-surface-100 mt-0.5">{user.phone || t('profile.noPhone')}</dd>
          </div>
        </dl>
      </section>

      {/* My reservations (camps / courses) */}
      <section className="bg-surface-900 rounded-xl p-6 border border-surface-800">
        <h2 className="flex items-center gap-2 text-xl font-bold text-surface-100 mb-5">
          <CalendarCheck className="w-5 h-5 text-primary-400" />
          {t('reservations.title')}
        </h2>

        {cancelError && <p className="text-sm text-rose-400/80 mb-4">{cancelError}</p>}

        {enrollmentsQuery.isLoading ? (
          <div className="flex justify-center py-8"><LoadingSpinner /></div>
        ) : enrollmentsQuery.isError ? (
          <p className="text-sm text-rose-400/80">{t('reservations.loadError')}</p>
        ) : isEmpty ? (
          <div className="flex flex-col items-center text-center py-8 px-4 rounded-lg border border-dashed border-surface-700">
            <p className="text-surface-300 font-medium">{t('reservations.empty')}</p>
            <p className="text-surface-500 text-sm mt-1 max-w-md">{t('reservations.emptyHint')}</p>
            <Link to="/obozy" className="mt-5">
              <Button variant="primary">{t('reservations.browse')}</Button>
            </Link>
          </div>
        ) : (
          <div className="space-y-6">
            {current.length > 0 && (
              <div>
                <h3 className="text-sm font-semibold uppercase tracking-wide text-surface-500 mb-3">{t('reservations.current')}</h3>
                <div className="space-y-3">{current.map(renderRow)}</div>
              </div>
            )}
            {past.length > 0 && (
              <div>
                <h3 className="text-sm font-semibold uppercase tracking-wide text-surface-500 mb-3">{t('reservations.past')}</h3>
                <div className="space-y-3 opacity-70">{past.map(renderRow)}</div>
              </div>
            )}
          </div>
        )}
      </section>

      {/* Moje treningi (cykliczne) */}
      <section className="bg-surface-900 rounded-xl p-6 border border-surface-800">
        <h2 className="flex items-center gap-2 text-xl font-bold text-surface-100 mb-5">
          <CalendarCheck className="w-5 h-5 text-primary-400" />
          {t('trainings.title')}
        </h2>

        {trainingsQuery.isLoading ? (
          <LoadingSpinner />
        ) : trainings.length ? (
          <div className="space-y-4">
            {monthTotal > 0 && (
              <div className="flex flex-wrap items-center justify-between gap-2 rounded-lg bg-primary-600/10 border border-primary-600/30 px-4 py-3">
                <span className="text-surface-200 text-sm">{t('trainings.monthTotalLabel', { month: formatMonth(cm) })}</span>
                <span className="text-primary-300 font-semibold">{monthTotal} zł</span>
              </div>
            )}
            {trainings.map(en => (
              <div key={en.id} className="rounded-lg border border-surface-800 bg-surface-800/30 p-4">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="font-semibold text-surface-100">{en.eventTypeName}</p>
                    <p className="flex items-center gap-1.5 text-sm text-surface-400 mt-1">
                      <Clock className="w-4 h-4" />
                      {t(`days.${en.dayOfWeek}`)} {en.startTime.slice(0, 5)}{en.endTime ? `–${en.endTime.slice(0, 5)}` : ''}
                    </p>
                    {en.instructorName && (
                      <p className="text-sm text-surface-400">{t('trainings.instructor', { name: en.instructorName })}</p>
                    )}
                    <p className="text-sm text-surface-500 mt-1">
                      {en.endMonth
                        ? t('trainings.periodUntil', { from: formatMonth(en.startMonth), to: formatMonth(en.endMonth) })
                        : t('trainings.period', { from: formatMonth(en.startMonth) }) + ' · ' + t('trainings.indefinite')}
                    </p>
                  </div>
                  {(en.endMonth == null || en.endMonth > cm) ? (
                    <Button variant="ghost" size="sm" onClick={() => setCancelTrainingId(en.id)} className="shrink-0 text-rose-400">
                      <Trash2 className="w-4 h-4 mr-1.5" />
                      {t('trainings.cancel')}
                    </Button>
                  ) : (
                    <span className="shrink-0 text-xs text-surface-500">{t('trainings.endsThisMonth')}</span>
                  )}
                </div>
                {en.monthlyAmount != null && (
                  <p className="text-sm text-primary-400 font-medium mt-3">
                    {t('trainings.billing', {
                      month: formatMonth(en.billingMonth),
                      amount: en.monthlyAmount,
                      sessions: en.sessionsInBillingMonth,
                    })}
                  </p>
                )}
                {en.cancelledDates.length > 0 && (
                  <p className="flex items-center gap-1.5 text-xs text-amber-400 mt-2">
                    <CalendarOff className="w-3.5 h-3.5 shrink-0" />
                    {t('trainings.cancelledDates', {
                      dates: en.cancelledDates.map((iso) => { const [, m, d] = iso.split('-'); return `${d}.${m}` }).join(', '),
                    })}
                  </p>
                )}
                <p className="text-xs text-surface-500 mt-1">{t('trainings.paymentNote')}</p>
              </div>
            ))}
          </div>
        ) : (
          <div className="flex flex-col items-center text-center py-8 px-4 rounded-lg border border-dashed border-surface-700">
            <p className="text-surface-300 font-medium">{t('trainings.empty')}</p>
            <p className="text-surface-500 text-sm mt-1 max-w-md">{t('trainings.emptyHint')}</p>
            <Link to="/treningi" className="mt-5">
              <Button variant="primary">{t('trainings.browse')}</Button>
            </Link>
          </div>
        )}
      </section>

      <ConfirmDialog
        isOpen={!!cancelTrainingId}
        onClose={() => setCancelTrainingId(null)}
        onConfirm={() => { if (cancelTrainingId) { cancelTrainingMut.mutate(cancelTrainingId); setCancelTrainingId(null) } }}
        title={t('trainings.cancelConfirmTitle')}
        message={t('trainings.cancelConfirm')}
        confirmLabel={t('trainings.cancel')}
        danger
      />
    </div>
  )
}
