import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { adminApi } from '../../api/admin'
import { Button } from '../../components/ui/Button'
import { Modal } from '../../components/ui/Modal'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { useToast } from '../../context/ToastContext'
import { visibleMonths, formatMonth } from '../../utils/trainingSchedule'
import { Pencil, Trash2, ChevronDown, ChevronRight, UserPlus, Check, X } from 'lucide-react'
import type { TrainingSlot, AdminUserSummary } from '../../types'
import clsx from 'clsx'

const DAYS = [1, 2, 3, 4, 5, 6, 7] as const

const emptyForm = {
  eventTypeId: '',
  instructorId: '',
  dayOfWeek: 1,
  startTime: '',
  endTime: '',
  price: '',
  maxParticipants: '',
}

const inputClass = 'w-full px-3 py-2 bg-surface-800 border border-surface-700 rounded-lg text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500'

function SlotRow({ slot, month, onEdit, onDelete, onToggleActive }: {
  slot: TrainingSlot
  month: string
  onEdit: (s: TrainingSlot) => void
  onDelete: (id: string) => void
  onToggleActive: (id: string) => void
}) {
  const { t } = useTranslation('admin')
  const { showToast } = useToast()
  const queryClient = useQueryClient()
  const [expanded, setExpanded] = useState(false)
  const [isAdding, setIsAdding] = useState(false)
  const [removeId, setRemoveId] = useState<string | null>(null)
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
    onSuccess: invalidateRoster,
    onError: (e: Error) => showToast(e.message, 'error'),
  })

  const roster = rosterQuery.data ?? []

  return (
    <div className={clsx('bg-surface-900 border border-surface-800 rounded-xl overflow-hidden', !slot.active && 'opacity-50')}>
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
        </div>
        <div className="flex items-center gap-1 shrink-0">
          <button onClick={() => onToggleActive(slot.id)} className={clsx('px-2 py-1 text-xs rounded', slot.active ? 'bg-green-900/30 text-green-400' : 'bg-surface-800 text-surface-500')}>
            {slot.active ? t('actions.deactivate') : t('actions.activate')}
          </button>
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
            <div className="overflow-x-auto">
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
                      </td>
                      <td className="py-2.5 pr-4">
                        <button
                          onClick={() => paymentMut.mutate({ id: r.enrollmentId, paid: !r.paid })}
                          className={clsx('inline-flex items-center gap-1 px-2 py-1 text-xs rounded', r.paid ? 'bg-green-900/30 text-green-400' : 'bg-surface-800 text-surface-400 hover:text-surface-200')}
                          title={r.paid ? t('trainingSlots.markUnpaid') : t('trainingSlots.markPaid')}
                        >
                          {r.paid ? <Check className="w-3.5 h-3.5" /> : <X className="w-3.5 h-3.5" />}
                          {r.paid ? t('trainingSlots.paid') : t('trainingSlots.unpaid')}
                        </button>
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
    </div>
  )
}

export function AdminTrainingSlots() {
  const { t } = useTranslation('admin')
  const { showToast } = useToast()
  const queryClient = useQueryClient()
  const months = visibleMonths()
  const [month, setMonth] = useState(months[0])
  const [editItem, setEditItem] = useState<TrainingSlot | null>(null)
  const [isCreating, setIsCreating] = useState(false)
  const [deleteId, setDeleteId] = useState<string | null>(null)
  const [form, setForm] = useState(emptyForm)

  const { data: slots, isLoading } = useQuery({
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
    mutationFn: () => adminApi.createTrainingSlot(buildPayload()),
    onSuccess: () => { invalidate(); setIsCreating(false) },
    onError: (e: Error) => showToast(e.message, 'error'),
  })
  const updateMut = useMutation({
    mutationFn: (id: string) => adminApi.updateTrainingSlot(id, buildPayload()),
    onSuccess: () => { invalidate(); setEditItem(null) },
    onError: (e: Error) => showToast(e.message, 'error'),
  })
  const deleteMut = useMutation({
    mutationFn: adminApi.deleteTrainingSlot,
    onSuccess: invalidate,
    onError: (e: Error) => showToast(e.message, 'error'),
  })
  const toggleMut = useMutation({ mutationFn: adminApi.toggleTrainingSlotActive, onSuccess: invalidate })

  const openCreate = () => { setForm(emptyForm); setIsCreating(true) }
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
    setEditItem(s)
  }
  const closeForm = () => { setIsCreating(false); setEditItem(null) }

  const isValid = form.eventTypeId && form.startTime && Number(form.maxParticipants) > 0
  const handleSave = () => {
    if (!isValid) return
    if (editItem) updateMut.mutate(editItem.id)
    else createMut.mutate()
  }

  if (isLoading) return <LoadingSpinner />

  return (
    <div>
      <div className="flex flex-wrap justify-between items-center gap-3 mb-4">
        <h2 className="text-xl font-semibold text-surface-100">{t('trainingSlots.title')}</h2>
        <Button variant="primary" size="sm" onClick={openCreate} disabled={!eventTypes?.length}>{t('actions.create')}</Button>
      </div>

      {/* Selektor miesiąca */}
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
        <div className="space-y-6">
          {DAYS.map(day => {
            const daySlots = slots.filter(s => s.dayOfWeek === day)
            if (!daySlots.length) return null
            return (
              <div key={day}>
                <h3 className="text-sm font-semibold uppercase tracking-wide text-primary-400 mb-2">{t(`days.${day}`)}</h3>
                <div className="space-y-2">
                  {daySlots.map(s => (
                    <SlotRow key={s.id} slot={s} month={month} onEdit={openEdit} onDelete={setDeleteId} onToggleActive={id => toggleMut.mutate(id)} />
                  ))}
                </div>
              </div>
            )
          })}
        </div>
      )}

      <Modal isOpen={isCreating || !!editItem} onClose={closeForm} title={editItem ? t('trainingSlots.editTitle') : t('trainingSlots.createTitle')}>
        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-surface-300 mb-1">{t('trainingSlots.eventType')}</label>
            <select value={form.eventTypeId} onChange={e => setForm(f => ({ ...f, eventTypeId: e.target.value }))} className={inputClass}>
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
              <input type="time" value={form.startTime} onChange={e => setForm(f => ({ ...f, startTime: e.target.value }))} className={inputClass} />
            </div>
            <div>
              <label className="block text-sm font-medium text-surface-300 mb-1">{t('trainingSlots.endTime')}</label>
              <input type="time" value={form.endTime} onChange={e => setForm(f => ({ ...f, endTime: e.target.value }))} className={inputClass} />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-surface-300 mb-1">{t('trainingSlots.maxParticipants')}</label>
              <input type="number" min={1} value={form.maxParticipants} onChange={e => setForm(f => ({ ...f, maxParticipants: e.target.value }))} className={inputClass} />
            </div>
            <div>
              <label className="block text-sm font-medium text-surface-300 mb-1">{t('trainingSlots.price')}</label>
              <input type="number" step="0.01" min={0} value={form.price} onChange={e => setForm(f => ({ ...f, price: e.target.value }))} className={inputClass} />
            </div>
          </div>
          <div className="flex justify-end gap-3">
            <Button variant="ghost" size="sm" onClick={closeForm}>{t('actions.cancel')}</Button>
            <Button variant="primary" size="sm" onClick={handleSave} disabled={!isValid} loading={createMut.isPending || updateMut.isPending}>{t('actions.save')}</Button>
          </div>
        </div>
      </Modal>

      <ConfirmDialog
        isOpen={!!deleteId}
        onClose={() => setDeleteId(null)}
        onConfirm={() => { if (deleteId) { deleteMut.mutate(deleteId); setDeleteId(null) } }}
        title={t('confirm.deleteTitle')}
        message={t('confirm.delete')}
        confirmLabel={t('actions.delete')}
        danger
      />
    </div>
  )
}
