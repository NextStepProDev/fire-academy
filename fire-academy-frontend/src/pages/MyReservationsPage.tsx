import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { ArrowLeft, CalendarCheck, Calendar, MapPin, X } from 'lucide-react'
import { userApi } from '../api/client'
import { Seo } from '../components/seo/Seo'
import { Button } from '../components/ui/Button'
import { Collapsible } from '../components/ui/Collapsible'
import { LoadingSpinner } from '../components/ui/LoadingSpinner'
import { formatSchedule } from '../utils/dates'
import { categoryToSlug } from '../utils/categorySlug'
import type { MyEnrollment } from '../types'

export function MyReservationsPage() {
  const { t } = useTranslation('account')
  const queryClient = useQueryClient()
  const [cancelError, setCancelError] = useState<string | null>(null)

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

  const current = enrollmentsQuery.data?.current ?? []
  const past = enrollmentsQuery.data?.past ?? []
  const isEmpty = current.length === 0 && past.length === 0

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
    <div className="max-w-3xl mx-auto px-4 py-10 space-y-6">
      <Seo title={t('reservations.title')} path="/moje-konto/rezerwacje" />

      <div>
        <Link to="/moje-konto" className="inline-flex items-center gap-1.5 text-sm text-surface-400 hover:text-primary-400 transition-colors">
          <ArrowLeft className="w-4 h-4" />
          {t('back')}
        </Link>
        <h1 className="mt-3 flex items-center gap-2.5 text-3xl md:text-4xl font-bold text-surface-100">
          <CalendarCheck className="w-7 h-7 text-primary-400" />
          {t('reservations.title')}
        </h1>
        <p className="text-surface-400 mt-2">{t('reservations.subtitle')}</p>
      </div>

      {cancelError && <p className="text-sm text-rose-400/80">{cancelError}</p>}

      {enrollmentsQuery.isLoading ? (
        <div className="flex justify-center py-8"><LoadingSpinner /></div>
      ) : enrollmentsQuery.isError ? (
        <p className="text-sm text-rose-400/80">{t('reservations.loadError')}</p>
      ) : isEmpty ? (
        <div className="flex flex-col items-center text-center py-12 px-4 rounded-xl border border-dashed border-surface-700">
          <p className="text-surface-300 font-medium">{t('reservations.empty')}</p>
          <p className="text-surface-500 text-sm mt-1 max-w-md">{t('reservations.emptyHint')}</p>
          <Link to="/obozy" className="mt-5">
            <Button variant="primary">{t('reservations.browse')}</Button>
          </Link>
        </div>
      ) : (
        <div className="space-y-4">
          <Collapsible title={t('reservations.current')} badge={current.length} defaultOpen>
            {current.length > 0 ? (
              <div className="space-y-3">{current.map(renderRow)}</div>
            ) : (
              <p className="text-sm text-surface-500">{t('reservations.noCurrent')}</p>
            )}
          </Collapsible>
          <Collapsible title={t('reservations.past')} badge={past.length} defaultOpen={current.length === 0}>
            {past.length > 0 ? (
              <div className="space-y-3 opacity-70">{past.map(renderRow)}</div>
            ) : (
              <p className="text-sm text-surface-500">{t('reservations.noPast')}</p>
            )}
          </Collapsible>
        </div>
      )}
    </div>
  )
}
