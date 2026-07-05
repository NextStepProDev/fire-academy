import { useMemo, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { adminApi } from '../../api/admin'
import { Button } from '../../components/ui/Button'
import { Modal } from '../../components/ui/Modal'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { useToast } from '../../context/ToastContext'
import { adminVisibleMonths, currentMonth, formatMonth } from '../../utils/trainingSchedule'
import { Pencil, Trash2, ChevronDown, ChevronRight, UserPlus, Check, X, Plus, CalendarOff, RotateCcw, Archive } from 'lucide-react'
import type { TrainingSlot, AdminUserSummary } from '../../types'
import clsx from 'clsx'

const DAYS = [1, 2, 3, 4, 5, 6, 7] as const
const TODAY_ISO = new Date().toISOString().slice(0, 10)

/** All dates (ISO) in a given month falling on a given day of the week (ISO 1–7). */
function sessionDatesInMonth(month: string, isoDayOfWeek: number): string[] {
  const [y, m] = month.split('-').map(Number)
  const out: string[] = []
  const last = new Date(y, m, 0).getDate()
  for (let d = 1; d <= last; d++) {
    const dt = new Date(y, m - 1, d)
    const iso = dt.getDay() === 0 ? 7 : dt.getDay()
    if (iso === isoDayOfWeek) out.push(`${month}-${String(d).padStart(2, '0')}`)
  }
  return out
}

const fmtDayMonth = (iso: string) => { const [, m, d] = iso.split('-'); return `${d}.${m}` }

const emptyForm = {
  eventTypeId: '',
  instructorId: '',
  dayOfWeek: 1,
  startTime: '',
  endTime: '',
  price: '',
  maxParticipants: '',
}

type SlotRowForm = { dayOfWeek: number; startTime: string; endTime: string; maxParticipants: string; price: string }
const emptyRow = (): SlotRowForm => ({ dayOfWeek: 1, startTime: '', endTime: '', maxParticipants: '', price: '' })
const emptyCreate = () => ({ eventTypeId: '', instructorId: '', rows: [emptyRow()] })

const inputClass = 'w-full px-3 py-2 bg-surface-800 border border-surface-700 rounded-lg text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500'

// Form field class.
// `error` (red border) is enabled ONLY after a save attempt, when a required field is empty.
// `paleEmpty` (for time fields) — when empty, the placeholder time is pale so it doesn't look like an entered value.
const fieldClass = (error?: boolean, paleEmpty?: boolean) => clsx(
  'w-full px-3 py-2 bg-surface-800 rounded-lg border focus:outline-none focus:ring-2 transition-colors',
  error ? 'border-rose-500 focus:ring-rose-500' : 'border-surface-700 focus:ring-primary-500',
  paleEmpty ? 'text-surface-500' : 'text-surface-100',
)

function SlotRow({ slot, month, onEdit, onDelete }: {
  slot: TrainingSlot
  month: string
  onEdit: (s: TrainingSlot) => void
  onDelete: (id: string) => void
}) {
  const { t } = useTranslation('admin')
  const { showToast } = useToast()
  const queryClient = useQueryClient()
  const [expanded, setExpanded] = useState(false)
  const [isAdding, setIsAdding] = useState(false)
  const [removeId, setRemoveId] = useState<string | null>(null)
  const [isDeactivating, setIsDeactivating] = useState(false)
  const [deactivateDate, setDeactivateDate] = useState(TODAY_ISO)
  const [confirmCancelDate, setConfirmCancelDate] = useState<string | null>(null)
  const [confirmRestoreDate, setConfirmRestoreDate] = useState<string | null>(null)
  const [addForm, setAddForm] = useState<{ query: string; selectedUser: AdminUserSummary | null; mode: 'indefinite' | 'fixed'; months: number }>({ query: '', selectedUser: null, mode: 'indefinite', months: 1 })

  const userSearch = useQuery({
    queryKey: ['admin', 'user-search', addForm.query],
    queryFn: () => adminApi.searchUsers(addForm.query),
    enabled: isAdding && addForm.query.trim().length >= 2 && !addForm.selectedUser,
  })

  const rosterQuery = useQuery({
    queryKey: ['admin', 'training-roster', slot.id, month],
    queryFn: () => adminApi.getTrainingRoster(slot.id, month),
    enabled: expanded,
  })

  const invalidateRoster = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'training-roster', slot.id, month] })
  }
  const invalidateCounts = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'training-slots'] })
    queryClient.invalidateQueries({ queryKey: ['public', 'training-slots'] })
  }
  const invalidateRefunds = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'training-refunds'] })
  }

  const addMut = useMutation({
    mutationFn: () => adminApi.addTrainingEnrollment(slot.id, {
      userId: addForm.selectedUser!.id,
      startMonth: month,
      months: addForm.mode === 'fixed' ? addForm.months : undefined,
    }),
    onSuccess: () => { invalidateRoster(); invalidateCounts(); setIsAdding(false); showToast(t('trainingSlots.addSuccess'), 'success') },
    onError: (e: Error) => showToast(e.message, 'error'),
  })
  const removeMut = useMutation({
    mutationFn: adminApi.removeTrainingEnrollment,
    onSuccess: () => { invalidateRoster(); invalidateCounts(); showToast(t('trainingSlots.removeSuccess'), 'success') },
    onError: (e: Error) => showToast(e.message, 'error'),
  })
  const paymentMut = useMutation({
    mutationFn: ({ id, paid }: { id: string; paid: boolean }) => adminApi.setTrainingPayment(id, { month, paid }),
    onSuccess: () => { invalidateRoster(); showToast(t('trainingSlots.paymentUpdated'), 'success') },
    onError: (e: Error) => showToast(e.message, 'error'),
  })
  const startMut = useMutation({
    mutationFn: ({ id, startDate }: { id: string; startDate: string | null }) => adminApi.setTrainingStart(id, startDate),
    onSuccess: () => {
      invalidateRoster()
      queryClient.invalidateQueries({ queryKey: ['admin', 'training-payments'] })
      showToast(t('trainingSlots.startUpdated'), 'success')
    },
    onError: (e: Error) => showToast(e.message, 'error'),
  })
  // Last day of the viewed month (ISO), to cap the "count from" date picker to that month.
  const monthLastDay = `${month}-${String(new Date(Number(month.slice(0, 4)), Number(month.slice(5, 7)), 0).getDate()).padStart(2, '0')}`

  const cancelledQuery = useQuery({
    queryKey: ['admin', 'cancelled-sessions', slot.id],
    queryFn: () => adminApi.getCancelledSessions(slot.id),
    enabled: expanded,
  })
  const cancelledSet = new Set((cancelledQuery.data ?? []).map(c => c.sessionDate))
  const invalidateCancelled = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'cancelled-sessions', slot.id] })
    queryClient.invalidateQueries({ queryKey: ['admin', 'cancelled-overview'] })
    queryClient.invalidateQueries({ queryKey: ['admin', 'training-refunds'] })
    invalidateCounts()
  }
  const cancelSessionMut = useMutation({
    mutationFn: (date: string) => adminApi.cancelTrainingSession(slot.id, date),
    onSuccess: () => { invalidateCancelled(); showToast(t('trainingSlots.sessionCancelled'), 'success') },
    onError: (e: Error) => showToast(e.message, 'error'),
  })
  const restoreSessionMut = useMutation({
    mutationFn: (date: string) => adminApi.restoreTrainingSession(slot.id, date),
    onSuccess: () => { invalidateCancelled(); showToast(t('trainingSlots.sessionRestored'), 'success') },
    onError: (e: Error) => showToast(e.message, 'error'),
  })
  const deactivateMut = useMutation({
    mutationFn: (date: string) => adminApi.deactivateTrainingSlot(slot.id, date),
    onSuccess: () => { invalidateCounts(); invalidateRefunds(); setIsDeactivating(false); showToast(t('trainingSlots.deactivateSuccess'), 'success') },
    onError: (e: Error) => showToast(e.message, 'error'),
  })
  const reactivateMut = useMutation({
    mutationFn: () => adminApi.reactivateTrainingSlot(slot.id),
    onSuccess: () => { invalidateCounts(); invalidateRefunds(); showToast(t('trainingSlots.reactivateSuccess'), 'success') },
    onError: (e: Error) => showToast(e.message, 'error'),
  })

  // All session dates of the month — past ones included so historical sessions that did not take place can be
  // cancelled too (that refunds subscribers who paid the month).
  const sessionDates = useMemo(
    () => sessionDatesInMonth(month, slot.dayOfWeek),
    [month, slot.dayOfWeek],
  )

  const roster = rosterQuery.data ?? []

  return (
    <div className={clsx('bg-surface-900 border border-surface-800 rounded-xl overflow-hidden', (!slot.active || slot.deactivatedFrom) && 'opacity-60')}>
      <div className="px-4 py-3 flex items-center gap-3">
        <button onClick={() => setExpanded(e => !e)} className="p-0.5 text-surface-400 shrink-0">
          {expanded ? <ChevronDown className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />}
        </button>
        <div className="flex-1 min-w-0 cursor-pointer" onClick={() => setExpanded(e => !e)}>
          <p className="font-medium text-surface-100 truncate">
            {slot.startTime?.slice(0, 5)}{slot.endTime ? `–${slot.endTime.slice(0, 5)}` : ''} · {slot.eventTypeName}
          </p>
          <p className="text-sm text-surface-400">
            {slot.instructorName ?? '—'}
            {slot.price != null && ` · ${slot.price} PLN`}
            {` · ${t('trainingSlots.roster')}: ${slot.enrolledThisMonth} / ${slot.maxParticipants}`}
          </p>
          {slot.deactivatedFrom && (
            <p className="flex items-center gap-1.5 text-xs text-amber-400 mt-0.5">
              <CalendarOff className="w-3.5 h-3.5 shrink-0" />
              {t('trainingSlots.inactiveFrom', { date: slot.deactivatedFrom.split('-').reverse().join('.') })}
            </p>
          )}
        </div>
        <div className="flex items-center gap-1 shrink-0">
          {slot.deactivatedFrom ? (
            slot.reactivatable ? (
              <button onClick={() => reactivateMut.mutate()} className="px-2 py-1 text-xs rounded bg-green-900/30 text-green-400">
                {t('actions.activate')}
              </button>
            ) : (
              <span className="px-2 py-1 text-xs rounded bg-rose-500/10 text-rose-300 cursor-not-allowed" title={t('trainingSlots.reactivateBlockedHint')}>
                {t('trainingSlots.reactivateBlocked')}
              </span>
            )
          ) : (
            <button onClick={() => { setDeactivateDate(TODAY_ISO); setIsDeactivating(true) }} className="px-2 py-1 text-xs rounded bg-surface-800 text-surface-400 hover:text-amber-400">
              {t('actions.deactivate')}
            </button>
          )}
          <button onClick={() => onEdit(slot)} className="p-1 text-surface-400 hover:text-primary-400"><Pencil className="w-4 h-4" /></button>
          <button onClick={() => onDelete(slot.id)} className="p-1 text-surface-400 hover:text-rose-400"><Trash2 className="w-4 h-4" /></button>
        </div>
      </div>

      {expanded && (
        <div className="border-t border-surface-800 px-4 py-3">
          <div className="flex justify-end mb-3">
            <Button variant="primary" size="sm" onClick={() => { setAddForm({ query: '', selectedUser: null, mode: 'indefinite', months: 1 }); setIsAdding(true) }}>
              <UserPlus className="w-4 h-4 mr-1.5" />
              {t('trainingSlots.addParticipant')}
            </Button>
          </div>

          {rosterQuery.isLoading ? (
            <LoadingSpinner />
          ) : !roster.length ? (
            <p className="text-sm text-surface-500 py-2">{t('trainingSlots.rosterEmpty')}</p>
          ) : (
            <div className={clsx('overflow-x-auto transition-opacity', rosterQuery.isFetching && 'opacity-60')}>
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-surface-800 text-left text-surface-400">
                    <th className="pb-2 pr-4">{t('enrollments.firstName')}</th>
                    <th className="pb-2 pr-4">{t('enrollments.email')}</th>
                    <th className="pb-2 pr-4">{t('enrollments.phone')}</th>
                    <th className="pb-2 pr-4">{t('trainingSlots.month')}</th>
                    <th className="pb-2 pr-4">{t('trainingSlots.paid')}</th>
                    <th className="pb-2"></th>
                  </tr>
                </thead>
                <tbody>
                  {roster.map(r => (
                    <tr key={r.enrollmentId} className="border-b border-surface-800/50">
                      <td className="py-2.5 pr-4 text-surface-100">{r.firstName} {r.lastName}</td>
                      <td className="py-2.5 pr-4 text-surface-300">{r.email}</td>
                      <td className="py-2.5 pr-4 text-surface-300">{r.phone}</td>
                      <td className="py-2.5 pr-4 text-surface-500">
                        {t('trainingSlots.since', { from: formatMonth(r.startMonth) })}
                        {r.indefinite ? ` · ${t('trainingSlots.indefinite').toLowerCase()}` : r.endMonth ? ` ${t('trainingSlots.until', { to: formatMonth(r.endMonth) })}` : ''}
                        {r.creditBalance > 0 && (
                          <span className="ml-2 inline-block px-2 py-0.5 text-xs rounded-full bg-emerald-900/30 text-emerald-400" title={t('trainingSlots.creditBalanceHint')}>
                            {t('trainingSlots.creditBalance', { amount: r.creditBalance })}
                          </span>
                        )}
                        {/* First-month discretionary start ("count from day X") — only meaningful in the start month. */}
                        {r.startMonth === month && !r.paid && (
                          <span className="flex items-center gap-1.5 mt-1 text-xs text-surface-400">
                            {t('trainingSlots.billFrom')}
                            <input
                              type="date"
                              min={`${month}-01`}
                              max={monthLastDay}
                              value={r.billableFrom ?? ''}
                              onChange={e => startMut.mutate({ id: r.enrollmentId, startDate: e.target.value || null })}
                              className="px-1.5 py-0.5 bg-surface-800 border border-surface-700 rounded text-surface-200"
                              title={t('trainingSlots.billFromHint')}
                            />
                          </span>
                        )}
                      </td>
                      <td className="py-2.5 pr-4">
                        <div className="flex items-center gap-2">
                          <button
                            onClick={() => paymentMut.mutate({ id: r.enrollmentId, paid: !r.paid })}
                            className={clsx('inline-flex items-center gap-1 px-2 py-1 text-xs rounded', r.paid ? 'bg-green-900/30 text-green-400' : 'bg-surface-800 text-surface-400 hover:text-surface-200')}
                            title={r.paid ? t('trainingSlots.markUnpaid') : t('trainingSlots.markPaid')}
                          >
                            {r.paid ? <Check className="w-3.5 h-3.5" /> : <X className="w-3.5 h-3.5" />}
                            {r.paid ? t('trainingSlots.paid') : t('trainingSlots.unpaid')}
                          </button>
                          <span className="text-xs text-surface-400">{r.amount} zł</span>
                          {r.overdue && (
                            <span className="inline-flex items-center px-2 py-0.5 text-xs rounded-full bg-rose-500/10 text-rose-300" title={t('trainingSlots.overdueHint')}>
                              {t('trainingSlots.overdue')}
                            </span>
                          )}
                        </div>
                      </td>
                      <td className="py-2.5 text-right">
                        <button onClick={() => setRemoveId(r.enrollmentId)} className="p-1 text-surface-400 hover:text-rose-400" title={t('trainingSlots.removeParticipant')}>
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {/* Cancelling individual sessions in the selected month */}
          <div className="mt-4 pt-3 border-t border-surface-800">
            <p className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-surface-400 mb-2">
              <CalendarOff className="w-3.5 h-3.5" /> {t('trainingSlots.cancelSessions')}
            </p>
            {!sessionDates.length ? (
              <p className="text-sm text-surface-500">{t('trainingSlots.noUpcomingSessions')}</p>
            ) : (
              <div className="flex flex-wrap gap-2">
                {sessionDates.map(date => {
                  const isCancelled = cancelledSet.has(date)
                  const isPast = date < TODAY_ISO
                  // Past cancellations can't be undone (a session that didn't happen can't un-happen).
                  const canRestore = isCancelled && !isPast
                  return (
                    <button
                      key={date}
                      onClick={() => { if (isCancelled) { if (canRestore) setConfirmRestoreDate(date) } else setConfirmCancelDate(date) }}
                      disabled={cancelSessionMut.isPending || restoreSessionMut.isPending || (isCancelled && !canRestore)}
                      className={clsx('inline-flex items-center gap-1.5 px-2.5 py-1 text-xs rounded-lg border transition-colors',
                        isCancelled
                          ? 'bg-amber-900/20 border-amber-700/50 text-amber-400 hover:bg-amber-900/30'
                          : 'bg-surface-800 border-surface-700 text-surface-300 hover:border-rose-500/60 hover:text-rose-400',
                        isPast && 'opacity-60',
                        isCancelled && !canRestore && 'cursor-default hover:bg-amber-900/20')}
                      title={isCancelled
                        ? (canRestore ? t('trainingSlots.restoreSession') : t('trainingSlots.cancelledPast'))
                        : (isPast ? t('trainingSlots.cancelSessionPast') : t('trainingSlots.cancelSession'))}
                    >
                      {isCancelled ? <RotateCcw className="w-3.5 h-3.5" /> : <CalendarOff className="w-3.5 h-3.5" />}
                      {fmtDayMonth(date)}
                    </button>
                  )
                })}
              </div>
            )}
            <p className="text-xs text-surface-500 mt-2">{t('trainingSlots.cancelSessionsHint')}</p>
          </div>
        </div>
      )}

      <Modal isOpen={isAdding} onClose={() => setIsAdding(false)} title={t('trainingSlots.addParticipant')}>
        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-surface-300 mb-1">{t('trainingSlots.participantSearch')}</label>
            {addForm.selectedUser ? (
              <div className="flex items-center justify-between gap-2 px-3 py-2 bg-surface-800 border border-surface-700 rounded-lg">
                <span className="text-surface-100 text-sm truncate">
                  {addForm.selectedUser.firstName} {addForm.selectedUser.lastName}
                  <span className="text-surface-500"> · {addForm.selectedUser.email}</span>
                </span>
                <button onClick={() => setAddForm(f => ({ ...f, selectedUser: null, query: '' }))} className="text-surface-400 hover:text-rose-400 shrink-0">
                  <X className="w-4 h-4" />
                </button>
              </div>
            ) : (
              <>
                <input
                  type="text"
                  value={addForm.query}
                  onChange={e => setAddForm(f => ({ ...f, query: e.target.value }))}
                  placeholder={t('trainingSlots.searchPlaceholder')}
                  className={inputClass}
                />
                {addForm.query.trim().length >= 2 && (
                  <div className="mt-1 max-h-48 overflow-y-auto rounded-lg border border-surface-700 divide-y divide-surface-800">
                    {userSearch.isLoading ? (
                      <p className="px-3 py-2 text-sm text-surface-500">…</p>
                    ) : userSearch.data?.length ? (
                      userSearch.data.map(u => (
                        <button
                          key={u.id}
                          onClick={() => setAddForm(f => ({ ...f, selectedUser: u }))}
                          className="w-full text-left px-3 py-2 text-sm text-surface-200 hover:bg-surface-800"
                        >
                          {u.firstName} {u.lastName} <span className="text-surface-500">· {u.email}</span>
                        </button>
                      ))
                    ) : (
                      <p className="px-3 py-2 text-sm text-surface-500">{t('trainingSlots.searchNoResults')}</p>
                    )}
                  </div>
                )}
              </>
            )}
            <p className="text-xs text-surface-500 mt-1">{t('trainingSlots.emailHint')}</p>
          </div>
          <div>
            <label className="block text-sm font-medium text-surface-300 mb-1">{t('trainingSlots.startMonth')}</label>
            <p className="px-3 py-2 bg-surface-800 border border-surface-700 rounded-lg text-surface-100 capitalize">{formatMonth(month)}</p>
          </div>
          <div>
            <label className="block text-sm font-medium text-surface-300 mb-1">{t('trainingSlots.duration')}</label>
            <div className="space-y-2">
              <label className="flex items-center gap-2 text-surface-200 text-sm">
                <input type="radio" checked={addForm.mode === 'indefinite'} onChange={() => setAddForm(f => ({ ...f, mode: 'indefinite' }))} />
                {t('trainingSlots.indefinite')}
              </label>
              <label className="flex items-center gap-2 text-surface-200 text-sm">
                <input type="radio" checked={addForm.mode === 'fixed'} onChange={() => setAddForm(f => ({ ...f, mode: 'fixed' }))} />
                {t('trainingSlots.fixed')}
              </label>
              {addForm.mode === 'fixed' && (
                <input type="number" min={1} max={24} value={addForm.months} onChange={e => setAddForm(f => ({ ...f, months: Math.max(1, Number(e.target.value)) }))} className={inputClass} />
              )}
            </div>
          </div>
          <div className="flex justify-end gap-3">
            <Button variant="ghost" size="sm" onClick={() => setIsAdding(false)}>{t('actions.cancel')}</Button>
            <Button variant="primary" size="sm" onClick={() => addMut.mutate()} disabled={!addForm.selectedUser} loading={addMut.isPending}>{t('actions.save')}</Button>
          </div>
        </div>
      </Modal>

      <ConfirmDialog
        isOpen={!!removeId}
        onClose={() => setRemoveId(null)}
        onConfirm={() => { if (removeId) { removeMut.mutate(removeId); setRemoveId(null) } }}
        title={t('trainingSlots.removeConfirmTitle')}
        message={t('trainingSlots.removeConfirm')}
        confirmLabel={t('trainingSlots.removeParticipant')}
        danger
      />

      <Modal isOpen={isDeactivating} onClose={() => setIsDeactivating(false)} title={t('trainingSlots.deactivateTitle')}>
        <div className="space-y-4">
          <p className="text-sm text-surface-400">{t('trainingSlots.deactivateHint')}</p>
          <div>
            <label className="block text-sm font-medium text-surface-300 mb-1">{t('trainingSlots.deactivateFrom')}</label>
            <input type="date" min={TODAY_ISO} value={deactivateDate} onChange={e => setDeactivateDate(e.target.value)} className={inputClass} />
          </div>
          <div className="flex justify-end gap-3">
            <Button variant="ghost" size="sm" onClick={() => setIsDeactivating(false)}>{t('actions.cancel')}</Button>
            <Button variant="primary" size="sm" onClick={() => deactivateMut.mutate(deactivateDate)} disabled={!deactivateDate} loading={deactivateMut.isPending}>{t('trainingSlots.deactivateConfirm')}</Button>
          </div>
        </div>
      </Modal>

      <ConfirmDialog
        isOpen={!!confirmCancelDate}
        onClose={() => setConfirmCancelDate(null)}
        onConfirm={() => { if (confirmCancelDate) { cancelSessionMut.mutate(confirmCancelDate); setConfirmCancelDate(null) } }}
        title={t('trainingSlots.cancelSessionConfirmTitle')}
        message={t('trainingSlots.cancelSessionConfirm', { date: confirmCancelDate ? fmtDayMonth(confirmCancelDate) : '' })}
        confirmLabel={t('trainingSlots.cancelSession')}
        danger
      />

      <ConfirmDialog
        isOpen={!!confirmRestoreDate}
        onClose={() => setConfirmRestoreDate(null)}
        onConfirm={() => { if (confirmRestoreDate) { restoreSessionMut.mutate(confirmRestoreDate); setConfirmRestoreDate(null) } }}
        title={t('cancelledSessions.restoreConfirmTitle')}
        message={t('cancelledSessions.restoreConfirm', { date: confirmRestoreDate ? fmtDayMonth(confirmRestoreDate) : '' })}
        confirmLabel={t('cancelledSessions.restore')}
      />
    </div>
  )
}

export function AdminTrainingSlots() {
  const { t } = useTranslation('admin')
  const { showToast } = useToast()
  const queryClient = useQueryClient()
  const months = adminVisibleMonths()   // includes a couple of past months for historical cancellations
  const [month, setMonth] = useState(currentMonth())
  // Day sections start collapsed — the admin expands the day they want.
  const [openDays, setOpenDays] = useState<Set<number>>(new Set())
  const toggleDay = (day: number) =>
    setOpenDays(prev => { const n = new Set(prev); if (n.has(day)) n.delete(day); else n.add(day); return n })
  const [editItem, setEditItem] = useState<TrainingSlot | null>(null)
  const [isCreating, setIsCreating] = useState(false)
  const [deleteId, setDeleteId] = useState<string | null>(null)
  const [form, setForm] = useState(emptyForm)
  const [createForm, setCreateForm] = useState(emptyCreate())
  const [showCreateErrors, setShowCreateErrors] = useState(false)
  const [showEditErrors, setShowEditErrors] = useState(false)
  const [showArchive, setShowArchive] = useState(false)

  const archiveQuery = useQuery({
    queryKey: ['admin', 'training-slots', 'deleted'],
    queryFn: adminApi.getDeletedTrainingSlots,
    enabled: showArchive,
  })

  const { data: slots, isLoading, isFetching } = useQuery({
    queryKey: ['admin', 'training-slots', month],
    queryFn: () => adminApi.getTrainingSlots(month),
    staleTime: 0,
  })
  const { data: eventTypes } = useQuery({
    queryKey: ['admin', 'event-types', 'TRAINING'],
    queryFn: () => adminApi.getEventTypes('TRAINING'),
  })
  const { data: instructors } = useQuery({
    queryKey: ['admin', 'instructors'],
    queryFn: adminApi.getInstructors,
  })
  const trainingInstructors = instructors?.filter(i => i.categories.includes('TRAINING')) ?? []

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'training-slots'] })
    queryClient.invalidateQueries({ queryKey: ['public', 'training-slots'] })
  }

  const buildPayload = () => ({
    eventTypeId: form.eventTypeId,
    instructorId: form.instructorId || undefined,
    dayOfWeek: Number(form.dayOfWeek),
    startTime: form.startTime,
    endTime: form.endTime || undefined,
    price: form.price ? Number(form.price) : undefined,
    maxParticipants: Number(form.maxParticipants),
  })

  const createMut = useMutation({
    mutationFn: () => adminApi.createTrainingSlotsBatch({
      eventTypeId: createForm.eventTypeId,
      instructorId: createForm.instructorId || undefined,
      slots: createForm.rows.map(r => ({
        dayOfWeek: Number(r.dayOfWeek),
        startTime: r.startTime,
        endTime: r.endTime || undefined,
        price: r.price ? Number(r.price) : undefined,
        maxParticipants: Number(r.maxParticipants),
      })),
    }),
    onSuccess: () => { invalidate(); setIsCreating(false); showToast(t('trainingSlots.createSuccess'), 'success') },
    onError: (e: Error) => showToast(e.message, 'error'),
  })
  const updateMut = useMutation({
    mutationFn: (id: string) => adminApi.updateTrainingSlot(id, buildPayload()),
    onSuccess: () => { invalidate(); setEditItem(null); showToast(t('trainingSlots.updateSuccess'), 'success') },
    onError: (e: Error) => showToast(e.message, 'error'),
  })
  const deleteMut = useMutation({
    mutationFn: adminApi.deleteTrainingSlot,
    onSuccess: invalidate,
    onError: (e: Error) => showToast(e.message, 'error'),
  })
  const openCreate = () => { setCreateForm(emptyCreate()); setShowCreateErrors(false); setIsCreating(true) }
  const handleCreate = () => {
    if (!createValid) { setShowCreateErrors(true); return }
    createMut.mutate()
  }
  const addRow = () => setCreateForm(f => ({ ...f, rows: [...f.rows, { ...f.rows[f.rows.length - 1] }] }))
  const removeRow = (i: number) => setCreateForm(f => ({ ...f, rows: f.rows.filter((_, idx) => idx !== i) }))
  const updateRow = (i: number, patch: Partial<SlotRowForm>) =>
    setCreateForm(f => ({ ...f, rows: f.rows.map((r, idx) => idx === i ? { ...r, ...patch } : r) }))
  const createValid = !!createForm.eventTypeId &&
    createForm.rows.every(r => r.startTime && Number(r.maxParticipants) > 0)

  const openEdit = (s: TrainingSlot) => {
    setForm({
      eventTypeId: s.eventTypeId,
      instructorId: s.instructorId ?? '',
      dayOfWeek: s.dayOfWeek,
      startTime: s.startTime?.slice(0, 5) ?? '',
      endTime: s.endTime?.slice(0, 5) ?? '',
      price: s.price?.toString() ?? '',
      maxParticipants: s.maxParticipants.toString(),
    })
    setShowEditErrors(false)
    setEditItem(s)
  }
  const closeForm = () => { setIsCreating(false); setEditItem(null); setShowCreateErrors(false); setShowEditErrors(false) }

  const isValid = form.eventTypeId && form.startTime && Number(form.maxParticipants) > 0
  const handleSave = () => {
    if (!editItem) return
    if (!isValid) { setShowEditErrors(true); return }
    updateMut.mutate(editItem.id)
  }

  if (isLoading) return <LoadingSpinner />

  return (
    <div>
      <div className="flex flex-wrap justify-between items-center gap-3 mb-4">
        <h2 className="text-xl font-semibold text-surface-100">{t('trainingSlots.title')}</h2>
        <div className="flex flex-wrap gap-2">
          <Button variant="primary" size="sm" onClick={openCreate} disabled={!eventTypes?.length}>{t('actions.create')}</Button>
        </div>
      </div>

      {/* Month selector */}
      <div className="flex flex-wrap gap-2 mb-6">
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

      {!eventTypes?.length && <p className="text-surface-500 mb-4">{t('trainingSlots.noTypesHint')}</p>}

      {!slots?.length ? (
        <p className="text-surface-500">{t('trainingSlots.noItems')}</p>
      ) : (
        <div className={clsx('space-y-6 transition-opacity', isFetching && 'opacity-60')}>
          {DAYS.map(day => {
            const daySlots = slots.filter(s => s.dayOfWeek === day)
            if (!daySlots.length) return null
            const open = openDays.has(day)
            return (
              <div key={day}>
                <button
                  onClick={() => toggleDay(day)}
                  className="flex w-full items-center gap-1.5 text-sm font-semibold uppercase tracking-wide text-primary-400 mb-2 hover:text-primary-300 transition-colors"
                >
                  {open ? <ChevronDown className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />}
                  {t(`days.${day}`)}
                  <span className="text-surface-500 normal-case font-normal">({daySlots.length})</span>
                </button>
                {open && (
                  <div className="space-y-2">
                    {daySlots.map(s => (
                      <SlotRow key={s.id} slot={s} month={month} onEdit={openEdit} onDelete={setDeleteId} />
                    ))}
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}

      {/* Archive of deleted slots (contact data of former participants) */}
      <div className="mt-8 border-t border-surface-800 pt-4">
        <button
          onClick={() => setShowArchive(s => !s)}
          className="flex items-center gap-2 text-sm font-semibold text-surface-300 hover:text-surface-100 transition-colors"
        >
          <Archive className="w-4 h-4" />
          {t('trainingSlots.archiveTitle')}
          {showArchive ? <ChevronDown className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />}
        </button>
        {showArchive && (
          <div className="mt-3">
            {archiveQuery.isLoading ? (
              <LoadingSpinner />
            ) : !archiveQuery.data?.length ? (
              <p className="text-sm text-surface-500">{t('trainingSlots.archiveEmpty')}</p>
            ) : (
              <div className="space-y-3">
                {archiveQuery.data.map(d => (
                  <div key={d.id} className="bg-surface-900 border border-surface-800 rounded-xl p-4">
                    <div className="flex flex-wrap items-center justify-between gap-2 mb-2">
                      <p className="font-medium text-surface-200">
                        {t(`days.${d.dayOfWeek}`)} {d.startTime.slice(0, 5)}{d.endTime ? `–${d.endTime.slice(0, 5)}` : ''} · {d.eventTypeName}
                        {d.instructorName && <span className="text-surface-500"> · {d.instructorName}</span>}
                      </p>
                      <span className="text-xs text-surface-500">{t('trainingSlots.archiveDeletedAt', { date: d.deletedAt.slice(0, 10) })}</span>
                    </div>
                    {!d.participants.length ? (
                      <p className="text-sm text-surface-500">{t('trainingSlots.archiveNoParticipants')}</p>
                    ) : (
                      <div className="overflow-x-auto">
                        <table className="w-full text-sm">
                          <thead>
                            <tr className="border-b border-surface-800 text-left text-surface-400">
                              <th className="pb-2 pr-4">{t('enrollments.firstName')}</th>
                              <th className="pb-2 pr-4">{t('enrollments.email')}</th>
                              <th className="pb-2 pr-4">{t('enrollments.phone')}</th>
                              <th className="pb-2 pr-4">{t('trainingSlots.month')}</th>
                            </tr>
                          </thead>
                          <tbody>
                            {d.participants.map((p, i) => (
                              <tr key={i} className="border-b border-surface-800/50">
                                <td className="py-2 pr-4 text-surface-100">{p.firstName} {p.lastName}</td>
                                <td className="py-2 pr-4 text-surface-300">{p.email}</td>
                                <td className="py-2 pr-4 text-surface-300">{p.phone}</td>
                                <td className="py-2 pr-4 text-surface-500">
                                  {t('trainingSlots.since', { from: formatMonth(p.startMonth) })}
                                  {p.endMonth ? ` ${t('trainingSlots.until', { to: formatMonth(p.endMonth) })}` : ` · ${t('trainingSlots.indefinite').toLowerCase()}`}
                                </td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>

      {/* Editing a single slot */}
      <Modal isOpen={!!editItem} onClose={closeForm} title={t('trainingSlots.editTitle')}>
        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-surface-300 mb-1">{t('trainingSlots.eventType')}</label>
            <select value={form.eventTypeId} onChange={e => setForm(f => ({ ...f, eventTypeId: e.target.value }))} className={fieldClass(showEditErrors && !form.eventTypeId)}>
              <option value="">{t('trainingSlots.selectEventType')}</option>
              {eventTypes?.map(et => <option key={et.id} value={et.id}>{et.name}</option>)}
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-surface-300 mb-1">{t('trainingSlots.instructor')}</label>
            <select value={form.instructorId} onChange={e => setForm(f => ({ ...f, instructorId: e.target.value }))} className={inputClass}>
              <option value="">{t('trainingSlots.noInstructor')}</option>
              {trainingInstructors.map(i => <option key={i.id} value={i.id}>{i.firstName} {i.lastName}</option>)}
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-surface-300 mb-1">{t('trainingSlots.dayOfWeek')}</label>
            <select value={form.dayOfWeek} onChange={e => setForm(f => ({ ...f, dayOfWeek: Number(e.target.value) }))} className={inputClass}>
              {DAYS.map(d => <option key={d} value={d}>{t(`days.${d}`)}</option>)}
            </select>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-surface-300 mb-1">{t('trainingSlots.startTime')}</label>
              <input type="time" value={form.startTime} onChange={e => setForm(f => ({ ...f, startTime: e.target.value }))} className={fieldClass(showEditErrors && !form.startTime, !form.startTime)} />
            </div>
            <div>
              <label className="block text-sm font-medium text-surface-300 mb-1">{t('trainingSlots.endTime')}</label>
              <input type="time" value={form.endTime} onChange={e => setForm(f => ({ ...f, endTime: e.target.value }))} className={fieldClass(false, !form.endTime)} />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-surface-300 mb-1">{t('trainingSlots.maxParticipants')}</label>
              <input type="number" min={1} value={form.maxParticipants} onChange={e => setForm(f => ({ ...f, maxParticipants: e.target.value }))} className={fieldClass(showEditErrors && !(Number(form.maxParticipants) > 0))} />
            </div>
            <div>
              <label className="block text-sm font-medium text-surface-300 mb-1">{t('trainingSlots.price')}</label>
              <input type="number" step="0.01" min={0} value={form.price} onChange={e => setForm(f => ({ ...f, price: e.target.value }))} className={inputClass} />
            </div>
          </div>
          <div className="flex justify-end gap-3">
            <Button variant="ghost" size="sm" onClick={closeForm}>{t('actions.cancel')}</Button>
            <Button variant="primary" size="sm" onClick={handleSave} loading={updateMut.isPending}>{t('actions.save')}</Button>
          </div>
        </div>
      </Modal>

      {/* Creating multiple slots at once (one type + instructor, multiple days) */}
      <Modal isOpen={isCreating} onClose={closeForm} title={t('trainingSlots.createTitle')}>
        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-surface-300 mb-1">{t('trainingSlots.eventType')}</label>
            <select value={createForm.eventTypeId} onChange={e => setCreateForm(f => ({ ...f, eventTypeId: e.target.value }))} className={fieldClass(showCreateErrors && !createForm.eventTypeId)}>
              <option value="">{t('trainingSlots.selectEventType')}</option>
              {eventTypes?.map(et => <option key={et.id} value={et.id}>{et.name}</option>)}
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-surface-300 mb-1">{t('trainingSlots.instructor')}</label>
            <select value={createForm.instructorId} onChange={e => setCreateForm(f => ({ ...f, instructorId: e.target.value }))} className={inputClass}>
              <option value="">{t('trainingSlots.noInstructor')}</option>
              {trainingInstructors.map(i => <option key={i.id} value={i.id}>{i.firstName} {i.lastName}</option>)}
            </select>
          </div>

          <div className="space-y-3">
            {createForm.rows.map((r, i) => (
              <div key={i} className="border border-surface-700 rounded-lg p-3 space-y-3">
                <div className="flex items-center justify-between gap-2">
                  <span className="text-xs font-semibold uppercase tracking-wide text-surface-400">{t('trainingSlots.dayOfWeek')} {i + 1}</span>
                  {createForm.rows.length > 1 && (
                    <button onClick={() => removeRow(i)} className="p-1 text-surface-400 hover:text-rose-400"><Trash2 className="w-4 h-4" /></button>
                  )}
                </div>
                <select value={r.dayOfWeek} onChange={e => updateRow(i, { dayOfWeek: Number(e.target.value) })} className={inputClass}>
                  {DAYS.map(d => <option key={d} value={d}>{t(`days.${d}`)}</option>)}
                </select>
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs text-surface-400 mb-1">{t('trainingSlots.startTime')}</label>
                    <input type="time" value={r.startTime} onChange={e => updateRow(i, { startTime: e.target.value })} className={fieldClass(showCreateErrors && !r.startTime, !r.startTime)} />
                  </div>
                  <div>
                    <label className="block text-xs text-surface-400 mb-1">{t('trainingSlots.endTime')}</label>
                    <input type="time" value={r.endTime} onChange={e => updateRow(i, { endTime: e.target.value })} className={fieldClass(false, !r.endTime)} />
                  </div>
                </div>
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs text-surface-400 mb-1">{t('trainingSlots.maxParticipants')}</label>
                    <input type="number" min={1} value={r.maxParticipants} onChange={e => updateRow(i, { maxParticipants: e.target.value })} className={fieldClass(showCreateErrors && !(Number(r.maxParticipants) > 0))} />
                  </div>
                  <div>
                    <label className="block text-xs text-surface-400 mb-1">{t('trainingSlots.price')}</label>
                    <input type="number" step="0.01" min={0} value={r.price} onChange={e => updateRow(i, { price: e.target.value })} className={inputClass} />
                  </div>
                </div>
              </div>
            ))}
            <button onClick={addRow} className="w-full py-2 border border-dashed border-surface-700 rounded-lg text-sm text-surface-300 hover:border-primary-500 hover:text-surface-100 transition-colors flex items-center justify-center gap-1.5">
              <Plus className="w-4 h-4" /> {t('trainingSlots.addDay')}
            </button>
          </div>

          <div className="flex justify-end gap-3">
            <Button variant="ghost" size="sm" onClick={closeForm}>{t('actions.cancel')}</Button>
            <Button variant="primary" size="sm" onClick={handleCreate} loading={createMut.isPending}>{t('trainingSlots.saveAll')}</Button>
          </div>
        </div>
      </Modal>

      <ConfirmDialog
        isOpen={!!deleteId}
        onClose={() => setDeleteId(null)}
        onConfirm={() => { if (deleteId) { deleteMut.mutate(deleteId); setDeleteId(null) } }}
        title={t('trainingSlots.deleteConfirmTitle')}
        message={t('trainingSlots.deleteConfirm')}
        confirmLabel={t('actions.delete')}
        danger
      />
    </div>
  )
}
