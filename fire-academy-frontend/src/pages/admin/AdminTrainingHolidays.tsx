import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { CalendarOff, Plus, Trash2 } from 'lucide-react'
import { adminApi } from '../../api/admin'
import { Button } from '../../components/ui/Button'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { useToast } from '../../context/ToastContext'
import { visibleMonths, formatMonth } from '../../utils/trainingSchedule'
import type { TrainingHoliday } from '../../types'
import clsx from 'clsx'

const TODAY_ISO = new Date().toISOString().slice(0, 10)
const fmtDate = (iso: string) => { const [y, m, d] = iso.split('-'); return `${d}.${m}.${y}` }

const inputClass = 'w-full px-3 py-2 bg-surface-800 border border-surface-700 rounded-lg text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500'

/** Whole-club days off — reduce the billed session count of every slot on that weekday. */
export function AdminTrainingHolidays() {
  const { t } = useTranslation('admin')
  const { showToast } = useToast()
  const queryClient = useQueryClient()
  const months = visibleMonths()
  const [month, setMonth] = useState(months[0])
  const [date, setDate] = useState('')
  const [label, setLabel] = useState('')
  const [removeTarget, setRemoveTarget] = useState<TrainingHoliday | null>(null)

  const monthStart = `${month}-01`
  const monthEnd = `${month}-${String(new Date(Number(month.split('-')[0]), Number(month.split('-')[1]), 0).getDate()).padStart(2, '0')}`
  const minDate = monthStart > TODAY_ISO ? monthStart : TODAY_ISO

  const { data: holidays, isLoading, isFetching } = useQuery({
    queryKey: ['admin', 'training-holidays', month],
    queryFn: () => adminApi.getTrainingHolidays(month),
    staleTime: 0,
  })

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'training-holidays'] })
    queryClient.invalidateQueries({ queryKey: ['admin', 'training-slots'] })
    queryClient.invalidateQueries({ queryKey: ['admin', 'training-refunds'] })
    queryClient.invalidateQueries({ queryKey: ['public', 'training-slots'] })
    queryClient.invalidateQueries({ queryKey: ['public', 'training-holidays'] })
  }

  const addMut = useMutation({
    mutationFn: () => adminApi.addTrainingHoliday({ date, label: label.trim() || undefined }),
    onSuccess: () => { invalidate(); setDate(''); setLabel(''); showToast(t('trainingHolidays.addSuccess'), 'success') },
    onError: (e: Error) => showToast(e.message, 'error'),
  })
  const removeMut = useMutation({
    mutationFn: adminApi.removeTrainingHoliday,
    onSuccess: () => { invalidate(); showToast(t('trainingHolidays.removeSuccess'), 'success') },
    onError: (e: Error) => showToast(e.message, 'error'),
  })

  // Removing a day off brings the sessions back. Blocked entirely if a cash refund was already paid out
  // (button disabled). Otherwise: warn to phone participants if they were emailed, else just confirm.
  const handleRemove = (h: TrainingHoliday) => {
    if (!h.restorable) { showToast(t('trainingHolidays.blockedHint'), 'error'); return }
    if (h.notifiedCount > 0) setRemoveTarget(h)
    else removeMut.mutate(h.id)
  }

  return (
    <div>
      <div className="flex items-center gap-2 mb-1">
        <CalendarOff className="w-5 h-5 text-primary-400" />
        <h2 className="text-xl font-semibold text-surface-100">{t('trainingHolidays.title')}</h2>
      </div>
      <p className="text-sm text-surface-500 mb-4">{t('trainingHolidays.hint')}</p>

      {/* Month selector */}
      <div className="flex flex-wrap gap-2 mb-4">
        {months.map(m => (
          <button
            key={m}
            onClick={() => setMonth(m)}
            className={clsx('px-3 py-1.5 text-sm font-medium rounded-lg capitalize transition-colors',
              m === month ? 'bg-primary-600 text-white' : 'bg-surface-900 border border-surface-800 text-surface-300 hover:bg-surface-800')}
          >
            {formatMonth(m)}
          </button>
        ))}
      </div>

      {/* Add form */}
      <div className="flex flex-col sm:flex-row gap-2 mb-4">
        <input
          type="date" value={date} min={minDate} max={monthEnd}
          onChange={e => setDate(e.target.value)}
          className={clsx(inputClass, 'sm:w-44', !date && 'text-surface-500')}
          aria-label={t('trainingHolidays.dateLabel')}
        />
        <input
          type="text" value={label} maxLength={120}
          onChange={e => setLabel(e.target.value)}
          placeholder={t('trainingHolidays.labelPlaceholder')}
          className={clsx(inputClass, 'flex-1')}
          aria-label={t('trainingHolidays.labelLabel')}
        />
        <Button variant="primary" size="sm" onClick={() => addMut.mutate()} disabled={!date} loading={addMut.isPending}>
          <Plus className="w-4 h-4 mr-1.5" />{t('trainingHolidays.add')}
        </Button>
      </div>

      {isLoading ? (
        <LoadingSpinner />
      ) : !holidays?.length ? (
        <p className="text-sm text-surface-500">{t('trainingHolidays.empty')}</p>
      ) : (
        <ul className={clsx('space-y-2 transition-opacity', isFetching && 'opacity-60')}>
          {holidays.map(h => (
            <li key={h.id} className="flex items-center justify-between gap-3 bg-surface-900 border border-surface-800 rounded-xl px-4 py-2.5">
              <span className="text-surface-100 text-sm">
                <span className="font-medium">{fmtDate(h.date)}</span>
                {h.label && <span className="text-surface-400"> · {h.label}</span>}
              </span>
              <button
                onClick={() => handleRemove(h)}
                disabled={!h.restorable}
                className={clsx('p-1', h.restorable ? 'text-surface-400 hover:text-rose-400' : 'text-surface-700 cursor-not-allowed')}
                title={h.restorable ? t('trainingHolidays.remove') : t('trainingHolidays.blockedHint')}
              >
                <Trash2 className="w-4 h-4" />
              </button>
            </li>
          ))}
        </ul>
      )}

      <ConfirmDialog
        isOpen={!!removeTarget}
        onClose={() => setRemoveTarget(null)}
        onConfirm={() => { if (removeTarget) { removeMut.mutate(removeTarget.id); setRemoveTarget(null) } }}
        title={t('trainingHolidays.removeConfirmTitle')}
        message={t('trainingHolidays.removeConfirm', {
          date: removeTarget ? fmtDate(removeTarget.date) : '',
          count: removeTarget?.notifiedCount ?? 0,
        })}
        confirmLabel={t('trainingHolidays.remove')}
      />
    </div>
  )
}
