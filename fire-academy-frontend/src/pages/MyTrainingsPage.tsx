import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { ArrowLeft, CalendarOff, Check, Clock, Dumbbell, Trash2 } from 'lucide-react'
import { userApi as trainingApi } from '../api/user'
import { Seo } from '../components/seo/Seo'
import { Button } from '../components/ui/Button'
import { Collapsible } from '../components/ui/Collapsible'
import { ConfirmDialog } from '../components/ui/ConfirmDialog'
import { LoadingSpinner } from '../components/ui/LoadingSpinner'
import { useToast } from '../context/ToastContext'
import { formatMonth, currentMonth } from '../utils/trainingSchedule'

export function MyTrainingsPage() {
  const { t } = useTranslation('account')
  const { showToast } = useToast()
  const queryClient = useQueryClient()
  const [cancelTrainingId, setCancelTrainingId] = useState<string | null>(null)

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

  const trainings = trainingsQuery.data ?? []
  const cm = currentMonth()
  // "To pay" excludes months the organizer already marked paid — those show as "paid" instead.
  const monthTotal = trainings
    .filter(e => e.billingMonth === cm && e.monthlyAmount != null && !e.billingMonthPaid)
    .reduce((sum, e) => sum + (e.monthlyAmount ?? 0), 0)
  const nextMonth = trainings.find(e => e.nextBillingMonth)?.nextBillingMonth ?? null
  const nextMonthTotal = trainings
    .filter(e => e.nextMonthAmount != null)
    .reduce((sum, e) => sum + (e.nextMonthAmount ?? 0), 0)
  // Grand total owed back across all trainings, so someone with several cancelled sessions/trainings
  // sees one sum instead of adding them up by hand.
  const totalRefund = trainings.reduce((sum, e) => sum + (e.pendingRefundAmount ?? 0), 0)
  // Grand total surplus waiting to reduce upcoming bills (across all trainings).
  const totalCredit = trainings.reduce((sum, e) => sum + (e.upcomingCreditBalance ?? 0), 0)
  // Grouped by weekday (ISO 1–7, Monday first) into collapsible sections, so a subscriber with several
  // trainings doesn't see them all dumped on screen at once.
  const trainingsByDay = [1, 2, 3, 4, 5, 6, 7]
    .map(day => ({ day, entries: trainings.filter(e => e.dayOfWeek === day) }))
    .filter(g => g.entries.length > 0)

  return (
    <div className="max-w-3xl mx-auto px-4 py-10 space-y-6">
      <Seo title={t('trainings.title')} path="/moje-konto/treningi" />

      <div>
        <Link to="/moje-konto" className="inline-flex items-center gap-1.5 text-sm text-surface-400 hover:text-primary-400 transition-colors">
          <ArrowLeft className="w-4 h-4" />
          {t('back')}
        </Link>
        <h1 className="mt-3 flex items-center gap-2.5 text-3xl md:text-4xl font-bold text-surface-100">
          <Dumbbell className="w-7 h-7 text-primary-400" />
          {t('trainings.title')}
        </h1>
        <p className="text-surface-400 mt-2">{t('trainings.subtitle')}</p>
      </div>

      {trainingsQuery.isLoading ? (
        <div className="flex justify-center py-8"><LoadingSpinner /></div>
      ) : trainingsQuery.isError ? (
        <div className="flex flex-col items-center text-center py-12 px-4 rounded-xl border border-dashed border-rose-500/40">
          <p className="text-surface-300 font-medium">{t('trainings.error')}</p>
          <Button variant="secondary" size="sm" onClick={() => trainingsQuery.refetch()} className="mt-4">{t('trainings.retry')}</Button>
        </div>
      ) : trainings.length ? (
        <div className="space-y-4">
          {monthTotal > 0 && (
            <div className="flex flex-wrap items-center justify-between gap-2 rounded-lg bg-primary-600/10 border border-primary-600/30 px-4 py-3">
              <span className="text-surface-200 text-sm">{t('trainings.monthTotalLabel', { month: formatMonth(cm) })}</span>
              <span className="text-primary-300 font-semibold">{monthTotal} zł</span>
            </div>
          )}
          {nextMonth && nextMonthTotal > 0 && (
            <div className="flex flex-wrap items-center justify-between gap-2 rounded-lg bg-surface-800/50 border border-surface-700 px-4 py-3">
              <span className="text-surface-300 text-sm">{t('trainings.nextMonthTotalLabel', { month: formatMonth(nextMonth) })}</span>
              <span className="text-surface-100 font-semibold">≈ {nextMonthTotal} zł</span>
            </div>
          )}
          {totalRefund > 0 && (
            <div className="flex flex-wrap items-center justify-between gap-2 rounded-lg bg-emerald-600/10 border border-emerald-600/30 px-4 py-3">
              <span className="text-surface-200 text-sm">{t('trainings.totalRefundLabel')}</span>
              <span className="text-emerald-300 font-semibold">{totalRefund} zł</span>
            </div>
          )}
          {totalCredit > 0 && (
            <div className="flex flex-wrap items-center justify-between gap-2 rounded-lg bg-emerald-600/10 border border-emerald-600/30 px-4 py-3">
              <span className="text-surface-200 text-sm">{t('trainings.totalCreditLabel')}</span>
              <span className="text-emerald-300 font-semibold">{totalCredit} zł</span>
            </div>
          )}
          {trainingsByDay.map(({ day, entries }) => (
            <Collapsible key={day} title={t(`days.${day}`)} badge={entries.length}>
              <div className="space-y-4">
                {entries.map(en => (
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
                        {en.slotDeactivatedFrom && (
                          <p className="flex items-center gap-1.5 text-sm text-amber-400 mt-1">
                            <CalendarOff className="w-4 h-4 shrink-0" />
                            {t('trainings.deactivatedFrom', { date: en.slotDeactivatedFrom.split('-').reverse().join('.') })}
                          </p>
                        )}
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
                      <>
                        {en.billingMonthPaid ? (
                          <p className="flex items-center gap-1.5 text-sm text-emerald-400 font-medium mt-3">
                            <Check className="w-4 h-4 shrink-0" />
                            {t('trainings.billingPaid', {
                              month: formatMonth(en.billingMonth),
                              amount: en.billingMonthPaidAmount ?? en.monthlyAmount,
                            })}
                          </p>
                        ) : (
                          <p className="text-sm text-primary-400 font-medium mt-3">
                            {t('trainings.billing', {
                              month: formatMonth(en.billingMonth),
                              amount: en.monthlyAmount,
                              sessions: en.sessionsInBillingMonth,
                            })}
                          </p>
                        )}
                        {en.monthlyCreditApplied > 0 && (
                          <p className="text-xs text-emerald-400 mt-0.5">
                            {t('trainings.creditApplied', { amount: en.monthlyCreditApplied })}
                          </p>
                        )}
                      </>
                    )}
                    {en.upcomingCreditBalance > 0 && (
                      <p className="flex items-center gap-1.5 text-xs text-emerald-400 mt-1">
                        <Check className="w-3.5 h-3.5 shrink-0" />
                        {t('trainings.upcomingCredit', { amount: en.upcomingCreditBalance })}
                      </p>
                    )}
                    {en.nextBillingMonth && en.nextMonthAmount != null && (
                      <div className="mt-2 rounded-lg border border-primary-600/30 bg-primary-600/10 px-3 py-2">
                        <p className="text-sm font-medium text-primary-300">
                          {t('trainings.nextBilling', {
                            month: formatMonth(en.nextBillingMonth),
                            amount: en.nextMonthAmount,
                            sessions: en.nextMonthSessions ?? 0,
                          })}
                        </p>
                        {en.nextMonthCreditApplied != null && en.nextMonthCreditApplied > 0 && (
                          <p className="text-xs text-emerald-400 mt-0.5">
                            {t('trainings.creditApplied', { amount: en.nextMonthCreditApplied })}
                          </p>
                        )}
                        <p className="text-xs text-surface-400 mt-0.5">{t('trainings.nextBillingNote')}</p>
                      </div>
                    )}
                    {en.pendingRefundAmount > 0 && (
                      <div className="mt-2 rounded-lg border border-emerald-600/30 bg-emerald-600/10 px-3 py-2">
                        <p className="text-sm font-medium text-emerald-300">
                          {t('trainings.pendingRefund', { amount: en.pendingRefundAmount })}
                        </p>
                        <p className="text-xs text-surface-400 mt-0.5">{t('trainings.pendingRefundNote')}</p>
                      </div>
                    )}
                    {en.cancelledDates.length > 0 && (
                      <p className="flex items-center gap-1.5 text-xs text-amber-400 mt-2">
                        <CalendarOff className="w-3.5 h-3.5 shrink-0" />
                        {t('trainings.cancelledDates', {
                          dates: en.cancelledDates.map((iso) => { const [, m, d] = iso.split('-'); return `${d}.${m}` }).join(', '),
                        })}
                      </p>
                    )}
                    {en.holidayDates.length > 0 && (
                      <p className="flex items-center gap-1.5 text-xs text-amber-400 mt-2">
                        <CalendarOff className="w-3.5 h-3.5 shrink-0" />
                        {t('trainings.holidayDates', {
                          dates: en.holidayDates.map((iso) => { const [, m, d] = iso.split('-'); return `${d}.${m}` }).join(', '),
                        })}
                      </p>
                    )}
                    <p className="text-xs text-surface-500 mt-1">{t('trainings.paymentNote')}</p>
                  </div>
                ))}
              </div>
            </Collapsible>
          ))}
        </div>
      ) : (
        <div className="flex flex-col items-center text-center py-12 px-4 rounded-xl border border-dashed border-surface-700">
          <p className="text-surface-300 font-medium">{t('trainings.empty')}</p>
          <p className="text-surface-500 text-sm mt-1 max-w-md">{t('trainings.emptyHint')}</p>
          <Link to="/treningi" className="mt-5">
            <Button variant="primary">{t('trainings.browse')}</Button>
          </Link>
        </div>
      )}

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
