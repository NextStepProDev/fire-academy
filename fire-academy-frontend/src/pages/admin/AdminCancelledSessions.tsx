import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { CalendarOff, RotateCcw, ChevronDown, ChevronRight, Phone, Check, Lock, UserX } from 'lucide-react'
import { adminApi } from '../../api/admin'
import { Button } from '../../components/ui/Button'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { Modal } from '../../components/ui/Modal'
import { useToast } from '../../context/ToastContext'
import type { CancelledSessionOverview } from '../../types'
import clsx from 'clsx'

const fmtDate = (iso: string) => { const [y, m, d] = iso.split('-'); return `${d}.${m}.${y}` }

const inputClass = 'w-full px-3 py-2 bg-surface-800 border border-surface-700 rounded-lg text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500'

/** Who has cancelled sessions, and when — club-wide. Upcoming sessions can be restored; the rest is archive. */
export function AdminCancelledSessions() {
  const { t } = useTranslation('admin')
  const { showToast } = useToast()
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)   // whole section collapsed by default
  const [showArchive, setShowArchive] = useState(false)
  const [restoreTarget, setRestoreTarget] = useState<CancelledSessionOverview | null>(null)
  // Cancel a whole trainer's day up front (e.g. sickness) — lives here, the home of cancellations.
  const [isCancellingInstructor, setIsCancellingInstructor] = useState(false)
  const [cancelInstructorId, setCancelInstructorId] = useState('')
  const [cancelDate, setCancelDate] = useState('')

  const overviewQuery = useQuery({
    queryKey: ['admin', 'cancelled-overview'],
    queryFn: adminApi.getCancelledSessionsOverview,
    staleTime: 0,
  })
  const { data: instructors } = useQuery({
    queryKey: ['admin', 'instructors'],
    queryFn: adminApi.getInstructors,
  })
  const trainingInstructors = instructors?.filter(i => i.categories.includes('TRAINING')) ?? []

  const cancelInstructorDayMut = useMutation({
    mutationFn: () => adminApi.cancelInstructorDay(cancelInstructorId, cancelDate),
    onSuccess: (res) => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'cancelled-overview'] })
      queryClient.invalidateQueries({ queryKey: ['admin', 'cancelled-sessions'] })
      queryClient.invalidateQueries({ queryKey: ['admin', 'training-refunds'] })
      queryClient.invalidateQueries({ queryKey: ['admin', 'training-slots'] })
      queryClient.invalidateQueries({ queryKey: ['public', 'training-slots'] })
      setIsCancellingInstructor(false)
      showToast(t('cancelledSessions.cancelInstructorSuccess', { count: res.cancelled }), 'success')
    },
    onError: (e: Error) => showToast(e.message, 'error'),
  })

  const restoreMut = useMutation({
    mutationFn: (item: CancelledSessionOverview) => adminApi.restoreTrainingSession(item.slotId, item.sessionDate),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'cancelled-overview'] })
      queryClient.invalidateQueries({ queryKey: ['admin', 'cancelled-sessions'] })
      queryClient.invalidateQueries({ queryKey: ['admin', 'training-refunds'] })
      queryClient.invalidateQueries({ queryKey: ['admin', 'training-slots'] })
      queryClient.invalidateQueries({ queryKey: ['public', 'training-slots'] })
      showToast(t('cancelledSessions.restored'), 'success')
    },
    onError: (e: Error) => showToast(e.message, 'error'),
  })

  const all = overviewQuery.data ?? []
  const upcoming = all.filter(i => i.future)
  const archive = all.filter(i => !i.future)

  const renderCard = (item: CancelledSessionOverview) => (
    <div key={item.id} className="bg-surface-900 border border-surface-800 rounded-xl p-4">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="flex items-center gap-1.5 font-medium text-surface-100">
            <CalendarOff className="w-4 h-4 text-amber-400 shrink-0" />
            {fmtDate(item.sessionDate)} · {t(`days.${item.dayOfWeek}`)} {item.startTime.slice(0, 5)}{item.endTime ? `–${item.endTime.slice(0, 5)}` : ''}
          </p>
          <p className="text-sm text-surface-400 mt-0.5">
            {item.eventTypeName}{item.instructorName ? ` · ${item.instructorName}` : ''}
          </p>
        </div>
        {item.future && (
          item.restorable ? (
            <Button variant="secondary" size="sm" onClick={() => setRestoreTarget(item)} disabled={restoreMut.isPending}>
              <RotateCcw className="w-3.5 h-3.5 mr-1.5" />
              {t('cancelledSessions.restore')}
            </Button>
          ) : (
            <span className="inline-flex items-center gap-1.5 px-2 py-1 text-xs rounded-lg bg-rose-500/10 text-rose-300 border border-rose-500/20 shrink-0"
              title={t('cancelledSessions.blockedHint')}>
              <Lock className="w-3.5 h-3.5" />
              {t('cancelledSessions.blocked')}
            </span>
          )
        )}
      </div>

      {!item.participants.length ? (
        <p className="text-sm text-surface-500 mt-3">{t('cancelledSessions.noParticipants')}</p>
      ) : (
        <ul className="mt-3 divide-y divide-surface-800/60">
          {item.participants.map((p, i) => (
            <li key={i} className="flex flex-wrap items-center justify-between gap-2 py-2">
              <div className="min-w-0">
                <span className="text-sm text-surface-100">{p.firstName} {p.lastName}</span>
                {p.phone && (
                  <span className="ml-2 inline-flex items-center gap-1 text-xs text-surface-400">
                    <Phone className="w-3 h-3" />{p.phone}
                  </span>
                )}
                <span className="block text-xs text-surface-500">{p.email}</span>
              </div>
              <div className="flex items-center gap-1.5 shrink-0">
                {p.owedRefund ? (
                  <span className="px-2 py-0.5 text-xs rounded-full bg-amber-900/30 text-amber-300">{t('cancelledSessions.owedRefund')}</span>
                ) : p.paid ? (
                  <span className="inline-flex items-center gap-1 px-2 py-0.5 text-xs rounded-full bg-green-900/30 text-green-400"><Check className="w-3 h-3" />{t('cancelledSessions.paid')}</span>
                ) : (
                  <span className="px-2 py-0.5 text-xs rounded-full bg-surface-800 text-surface-400">{t('cancelledSessions.unpaid')}</span>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  )

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3 mb-1">
        <button onClick={() => setOpen(o => !o)} className="flex items-center gap-2 text-left min-w-0">
          {open ? <ChevronDown className="w-5 h-5 text-surface-400 shrink-0" /> : <ChevronRight className="w-5 h-5 text-surface-400 shrink-0" />}
          <CalendarOff className="w-5 h-5 text-primary-400 shrink-0" />
          <h2 className="text-xl font-semibold text-surface-100">{t('cancelledSessions.title')}</h2>
          {upcoming.length > 0 && <span className="text-xs text-surface-500">({t('cancelledSessions.upcomingCount', { count: upcoming.length })})</span>}
        </button>
        <Button variant="secondary" size="sm"
          onClick={() => { setCancelInstructorId(''); setCancelDate(''); setIsCancellingInstructor(true) }}
          disabled={!trainingInstructors.length}>
          <UserX className="w-4 h-4 mr-1.5" />{t('cancelledSessions.cancelInstructor')}
        </Button>
      </div>

      {open && (overviewQuery.isLoading ? (
        <LoadingSpinner />
      ) : (
        <>
          <p className="text-sm text-surface-500 mb-4">{t('cancelledSessions.hint')}</p>
          <h3 className="text-sm font-semibold uppercase tracking-wide text-primary-400 mb-2">{t('cancelledSessions.upcoming')}</h3>
          {!upcoming.length ? (
            <p className="text-sm text-surface-500">{t('cancelledSessions.upcomingEmpty')}</p>
          ) : (
            <div className={clsx('space-y-3 transition-opacity', overviewQuery.isFetching && 'opacity-60')}>
              {upcoming.map(renderCard)}
            </div>
          )}

          {/* Archive: past cancelled sessions (read-only history) */}
          <div className="mt-6 border-t border-surface-800 pt-4">
            <button
              onClick={() => setShowArchive(s => !s)}
              className="flex items-center gap-2 text-sm font-semibold text-surface-300 hover:text-surface-100 transition-colors"
            >
              {showArchive ? <ChevronDown className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />}
              {t('cancelledSessions.archiveTitle')}
              {!!archive.length && <span className="text-xs text-surface-500">({archive.length})</span>}
            </button>
            {showArchive && (
              <div className="mt-3">
                {!archive.length ? (
                  <p className="text-sm text-surface-500">{t('cancelledSessions.archiveEmpty')}</p>
                ) : (
                  <div className="space-y-3 opacity-80">{archive.map(renderCard)}</div>
                )}
              </div>
            )}
          </div>
        </>
      ))}

      {/* Cancel all of one instructor's sessions on a date (e.g. instructor unavailable) */}
      <Modal isOpen={isCancellingInstructor} onClose={() => setIsCancellingInstructor(false)} title={t('cancelledSessions.cancelInstructorTitle')}>
        <div className="space-y-4">
          <p className="text-sm text-surface-400">{t('cancelledSessions.cancelInstructorHint')}</p>
          <div>
            <label className="block text-sm font-medium text-surface-300 mb-1">{t('cancelledSessions.instructor')}</label>
            <select value={cancelInstructorId} onChange={e => setCancelInstructorId(e.target.value)} className={inputClass}>
              <option value="">{t('cancelledSessions.selectInstructor')}</option>
              {trainingInstructors.map(i => <option key={i.id} value={i.id}>{i.firstName} {i.lastName}</option>)}
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-surface-300 mb-1">{t('cancelledSessions.cancelInstructorDate')}</label>
            {/* Past dates allowed — a historical day that didn't take place still refunds paid subscribers. */}
            <input type="date" value={cancelDate} onChange={e => setCancelDate(e.target.value)} className={clsx(inputClass, !cancelDate && 'text-surface-500')} />
            <p className="text-xs text-surface-500 mt-1">{t('cancelledSessions.cancelInstructorPastHint')}</p>
          </div>
          <div className="flex justify-end gap-3">
            <Button variant="ghost" size="sm" onClick={() => setIsCancellingInstructor(false)}>{t('actions.cancel')}</Button>
            <Button variant="primary" size="sm" onClick={() => cancelInstructorDayMut.mutate()} disabled={!cancelInstructorId || !cancelDate} loading={cancelInstructorDayMut.isPending}>
              {t('cancelledSessions.cancelInstructorConfirm')}
            </Button>
          </div>
        </div>
      </Modal>

      <ConfirmDialog
        isOpen={!!restoreTarget}
        onClose={() => setRestoreTarget(null)}
        onConfirm={() => { if (restoreTarget) { restoreMut.mutate(restoreTarget); setRestoreTarget(null) } }}
        title={t('cancelledSessions.restoreConfirmTitle')}
        message={t('cancelledSessions.restoreConfirm', {
          date: restoreTarget ? fmtDate(restoreTarget.sessionDate) : '',
        })}
        confirmLabel={t('cancelledSessions.restore')}
      />
    </div>
  )
}
