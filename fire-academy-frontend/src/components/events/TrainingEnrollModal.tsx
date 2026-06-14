import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Modal } from '../ui/Modal'
import { Button } from '../ui/Button'
import { userApi } from '../../api/user'
import { useToast } from '../../context/ToastContext'
import { monthlyOccurrences, formatMonth } from '../../utils/trainingSchedule'
import type { TrainingSlotCard } from '../../types'

interface TrainingEnrollModalProps {
  slot: TrainingSlotCard | null
  startMonth: string
  onClose: () => void
}

export function TrainingEnrollModal({ slot, startMonth, onClose }: TrainingEnrollModalProps) {
  const { t } = useTranslation('events')
  const { showToast } = useToast()
  const queryClient = useQueryClient()
  const [mode, setMode] = useState<'indefinite' | 'fixed'>('indefinite')
  const [months, setMonths] = useState(1)

  const myEnrollments = useQuery({
    queryKey: ['user', 'training-enrollments'],
    queryFn: userApi.getMyTrainingEnrollments,
    enabled: !!slot,
  })

  const enrollMut = useMutation({
    mutationFn: () => userApi.enrollTrainingSlot(slot!.id, {
      startMonth,
      months: mode === 'fixed' ? months : undefined,
    }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['public', 'training-slots'] })
      queryClient.invalidateQueries({ queryKey: ['user', 'training-enrollments'] })
      showToast(t('enrollTraining.success'), 'success')
      onClose()
    },
    onError: (e: Error) => showToast(e.message, 'error'),
  })

  if (!slot) return null

  const sessions = monthlyOccurrences(slot.dayOfWeek, startMonth)
  const amount = slot.price != null ? slot.price * sessions : null

  // Suma z dotychczasowymi rezerwacjami użytkownika obejmującymi wybrany miesiąc.
  const existingForMonth = (myEnrollments.data ?? [])
    .filter(e => e.startMonth <= startMonth && (e.endMonth == null || e.endMonth >= startMonth) && e.price != null)
    .reduce((sum, e) => sum + (e.price! * monthlyOccurrences(e.dayOfWeek, startMonth)), 0)
  const cumulative = existingForMonth + (amount ?? 0)

  const inputClass = 'w-full px-3 py-2 bg-surface-800 border border-surface-700 rounded-lg text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500'

  return (
    <Modal isOpen onClose={onClose} title={t('enrollTraining.title')}>
      <div className="space-y-4">
        <div className="text-surface-300 text-sm space-y-1">
          <p className="text-surface-100 font-medium">
            {t('enrollTraining.summary', {
              day: t(`days.${slot.dayOfWeek}`),
              time: `${slot.startTime.slice(0, 5)}${slot.endTime ? `–${slot.endTime.slice(0, 5)}` : ''}`,
              name: slot.eventTypeName,
            })}
          </p>
          {slot.instructorName && <p>{t('enrollTraining.instructor', { name: slot.instructorName })}</p>}
        </div>

        <div>
          <label className="block text-sm font-medium text-surface-300 mb-1">{t('enrollTraining.startMonth')}</label>
          <p className="px-3 py-2 bg-surface-800 border border-surface-700 rounded-lg text-surface-100 capitalize">{formatMonth(startMonth)}</p>
        </div>

        <div>
          <label className="block text-sm font-medium text-surface-300 mb-1">{t('enrollTraining.duration')}</label>
          <div className="space-y-2">
            <label className="flex items-center gap-2 text-surface-200 text-sm">
              <input type="radio" checked={mode === 'indefinite'} onChange={() => setMode('indefinite')} />
              {t('enrollTraining.indefinite')}
            </label>
            <label className="flex items-center gap-2 text-surface-200 text-sm">
              <input type="radio" checked={mode === 'fixed'} onChange={() => setMode('fixed')} />
              {t('enrollTraining.fixed')}
            </label>
            {mode === 'fixed' && (
              <input
                type="number" min={1} max={24} value={months}
                onChange={e => setMonths(Math.max(1, Number(e.target.value)))}
                className={inputClass}
                aria-label={t('enrollTraining.monthsCount')}
              />
            )}
          </div>
        </div>

        {amount != null && (
          <p className="text-sm text-primary-400 font-medium">
            {t('enrollTraining.summaryAmount', { month: formatMonth(startMonth), amount, sessions, price: slot.price })}
          </p>
        )}
        {existingForMonth > 0 && (
          <p className="text-sm text-surface-200">
            {t('enrollTraining.cumulative', { month: formatMonth(startMonth), amount: cumulative })}
          </p>
        )}
        <p className="text-xs text-surface-500">{t('enrollTraining.standingNote')}</p>
        <p className="text-xs text-surface-500">{t('enrollTraining.paymentNote')}</p>
        <p className="text-xs text-surface-500">{t('enrollTraining.accountHint')}</p>

        <div className="flex justify-end gap-3">
          <Button variant="ghost" size="sm" onClick={onClose}>{t('enrollTraining.cancel')}</Button>
          <Button variant="primary" size="sm" onClick={() => enrollMut.mutate()} loading={enrollMut.isPending}>
            {t('enrollTraining.submit')}
          </Button>
        </div>
      </div>
    </Modal>
  )
}
