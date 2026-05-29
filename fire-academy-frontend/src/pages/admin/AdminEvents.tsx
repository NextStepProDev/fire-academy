import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { adminApi } from '../../api/admin'
import { Button } from '../../components/ui/Button'
import { Modal } from '../../components/ui/Modal'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { Pencil, Trash2 } from 'lucide-react'
import type { EventCategory, EventInstance } from '../../types'
import clsx from 'clsx'

interface AdminEventsProps {
  category: EventCategory
}

export function AdminEvents({ category }: AdminEventsProps) {
  const { t } = useTranslation('admin')
  const queryClient = useQueryClient()
  const [editItem, setEditItem] = useState<EventInstance | null>(null)
  const [isCreating, setIsCreating] = useState(false)
  const [deleteId, setDeleteId] = useState<string | null>(null)
  const [showCloseConfirm, setShowCloseConfirm] = useState(false)
  const [form, setForm] = useState({ eventTypeName: '', description: '', startDate: '', endDate: '', startTime: '', endTime: '', location: '', price: '', maxParticipants: '' })

  const queryKey = ['admin', 'events', category]
  const { data: events, isLoading } = useQuery({ queryKey, queryFn: () => adminApi.getEvents(category), staleTime: 0 })
  const { data: eventTypes } = useQuery({ queryKey: ['admin', 'event-types', category], queryFn: () => adminApi.getEventTypes(category), staleTime: 0 })

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey })
    queryClient.invalidateQueries({ queryKey: ['admin', 'event-types', category] })
  }

  const createMut = useMutation({
    mutationFn: () => {
      const matchedType = eventTypes?.find(et => et.name === form.eventTypeName)
      return adminApi.createEvent({
        eventTypeId: matchedType?.id,
        customName: matchedType ? undefined : form.eventTypeName,
        description: form.description || undefined,
        category,
        startDate: form.startDate,
        endDate: form.endDate || undefined,
        startTime: form.startTime || undefined,
        endTime: form.endTime || undefined,
        location: form.location || undefined,
        price: form.price ? Number(form.price) : undefined,
        maxParticipants: form.maxParticipants ? Number(form.maxParticipants) : undefined,
      })
    },
    onSuccess: invalidate,
  })
  const updateMut = useMutation({
    mutationFn: (id: string) => {
      const matchedType = eventTypes?.find(et => et.name === form.eventTypeName)
      return adminApi.updateEvent(id, {
        eventTypeId: matchedType?.id,
        customName: matchedType ? undefined : form.eventTypeName || undefined,
        description: form.description || undefined,
        startDate: form.startDate,
        endDate: form.endDate || undefined,
        startTime: form.startTime || undefined,
        endTime: form.endTime || undefined,
        location: form.location || undefined,
        price: form.price ? Number(form.price) : undefined,
        maxParticipants: form.maxParticipants ? Number(form.maxParticipants) : undefined,
      })
    },
    onSuccess: invalidate,
  })
  const deleteMut = useMutation({ mutationFn: adminApi.deleteEvent, onSuccess: invalidate })
  const toggleMut = useMutation({ mutationFn: adminApi.toggleEventActive, onSuccess: invalidate })

  const openCreate = () => {
    setForm({ eventTypeName: '', description: '', startDate: '', endDate: '', startTime: '', endTime: '', location: '', price: '', maxParticipants: '' })
    setIsCreating(true)
  }
  const openEdit = (ev: EventInstance) => {
    setForm({
      eventTypeName: ev.eventTypeName,
      description: ev.description ?? '',
      startDate: ev.startDate,
      endDate: ev.endDate ?? '',
      startTime: ev.startTime ?? '',
      endTime: ev.endTime ?? '',
      location: ev.location ?? '',
      price: ev.price?.toString() ?? '',
      maxParticipants: ev.maxParticipants?.toString() ?? '',
    })
    setEditItem(ev)
  }

  const isDirty = Object.values(form).some(v => v !== '')
  const canSave = isCreating ? (form.eventTypeName && form.startDate) : !!form.startDate

  const closeForm = () => { setIsCreating(false); setEditItem(null); setShowCloseConfirm(false) }

  const handleClose = () => {
    if (!isDirty) { closeForm(); return }
    setShowCloseConfirm(true)
  }

  const handleSave = async () => {
    if (editItem) {
      await updateMut.mutateAsync(editItem.id)
      setEditItem(null)
    } else {
      await createMut.mutateAsync()
      setIsCreating(false)
    }
    setShowCloseConfirm(false)
  }

  if (isLoading) return <LoadingSpinner />

  return (
    <div>
      <div className="flex justify-between items-center mb-6">
        <h2 className="text-xl font-semibold text-surface-100">{t('events.title')}</h2>
        <Button variant="primary" size="sm" onClick={openCreate}>{t('actions.create')}</Button>
      </div>

      {!events?.length ? (
        <p className="text-surface-500">{t('events.noItems')}</p>
      ) : (
        <div className="space-y-3">
          {events.map(ev => (
            <div key={ev.id} className={clsx('flex items-center gap-4 bg-surface-900 border border-surface-800 rounded-xl p-4', !ev.active && 'opacity-50')}>
              <div className="flex-1 min-w-0">
                <p className="font-medium text-surface-100">{ev.eventTypeName}</p>
                <p className="text-sm text-surface-400">
                  {ev.startDate}{ev.endDate ? ` – ${ev.endDate}` : ''}
                  {ev.startTime ? ` · ${ev.startTime}${ev.endTime ? ` – ${ev.endTime}` : ''}` : ''}
                  {ev.location ? ` · ${ev.location}` : ''}
                </p>
                <p className="text-sm text-surface-500">
                  {ev.price != null && `${ev.price} PLN · `}
                  {t('events.enrolled')}: {(ev as unknown as { enrollmentCount?: number }).enrollmentCount ?? 0}
                  {ev.maxParticipants != null && ` / ${ev.maxParticipants}`}
                </p>
              </div>
              <div className="flex items-center gap-1">
                <button onClick={() => toggleMut.mutate(ev.id)} className={clsx('px-2 py-1 text-xs rounded', ev.active ? 'bg-green-900/30 text-green-400' : 'bg-surface-800 text-surface-500')}>
                  {ev.active ? t('actions.deactivate') : t('actions.activate')}
                </button>
                <button onClick={() => openEdit(ev)} className="p-1 text-surface-400 hover:text-primary-400"><Pencil className="w-4 h-4" /></button>
                <button onClick={() => setDeleteId(ev.id)} className="p-1 text-surface-400 hover:text-rose-400"><Trash2 className="w-4 h-4" /></button>
              </div>
            </div>
          ))}
        </div>
      )}

      <Modal
        isOpen={isCreating || !!editItem}
        onClose={handleClose}
        title={editItem ? t('events.editTitle') : t('events.createTitle')}
      >
        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-surface-300 mb-1">{t('events.eventType')}</label>
            <input
              list={`event-types-${category}`}
              value={form.eventTypeName}
              onChange={e => setForm(f => ({ ...f, eventTypeName: e.target.value }))}
              placeholder={t('events.typeOrSelect')}
              className="w-full px-3 py-2 bg-surface-800 border border-surface-700 rounded-lg text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
            />
            <datalist id={`event-types-${category}`}>
              {eventTypes?.map(et => <option key={et.id} value={et.name} />)}
            </datalist>
          </div>
          {(form.eventTypeName && !eventTypes?.find(et => et.name === form.eventTypeName)) && (
            <div>
              <label className="block text-sm font-medium text-surface-300 mb-1">{t('events.description')}</label>
              <textarea value={form.description} onChange={e => setForm(f => ({ ...f, description: e.target.value }))} rows={3} className="w-full px-3 py-2 bg-surface-800 border border-surface-700 rounded-lg text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none" />
            </div>
          )}
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-surface-300 mb-1">{t('events.startDate')}</label>
              <input type="date" value={form.startDate} onChange={e => setForm(f => ({ ...f, startDate: e.target.value }))} className="w-full px-3 py-2 bg-surface-800 border border-surface-700 rounded-lg text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500" />
            </div>
            <div>
              <label className="block text-sm font-medium text-surface-300 mb-1">{t('events.endDate')}</label>
              <input type="date" value={form.endDate} min={form.startDate || undefined} onChange={e => setForm(f => ({ ...f, endDate: e.target.value }))} className="w-full px-3 py-2 bg-surface-800 border border-surface-700 rounded-lg text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500" />
            </div>
          </div>
          <div className="grid grid-cols-3 gap-4">
            <div>
              <label className="block text-sm font-medium text-surface-300 mb-1">{t('events.startTime')}</label>
              <input type="time" value={form.startTime} onChange={e => setForm(f => ({ ...f, startTime: e.target.value }))} className="w-full px-3 py-2 bg-surface-800 border border-surface-700 rounded-lg text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500" />
            </div>
            <div>
              <label className="block text-sm font-medium text-surface-300 mb-1">{t('events.endTime')}</label>
              <input type="time" value={form.endTime} onChange={e => setForm(f => ({ ...f, endTime: e.target.value }))} className="w-full px-3 py-2 bg-surface-800 border border-surface-700 rounded-lg text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500" />
            </div>
            <div>
              <label className="block text-sm font-medium text-surface-300 mb-1">{t('events.location')}</label>
              <input value={form.location} onChange={e => setForm(f => ({ ...f, location: e.target.value }))} className="w-full px-3 py-2 bg-surface-800 border border-surface-700 rounded-lg text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500" />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-surface-300 mb-1">{t('events.price')}</label>
              <input type="number" step="0.01" value={form.price} onChange={e => setForm(f => ({ ...f, price: e.target.value }))} className="w-full px-3 py-2 bg-surface-800 border border-surface-700 rounded-lg text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500" />
            </div>
            <div>
              <label className="block text-sm font-medium text-surface-300 mb-1">{t('events.maxParticipants')}</label>
              <input type="number" value={form.maxParticipants} onChange={e => setForm(f => ({ ...f, maxParticipants: e.target.value }))} className="w-full px-3 py-2 bg-surface-800 border border-surface-700 rounded-lg text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500" />
            </div>
          </div>
          <div className="flex justify-end gap-3">
            <Button variant="ghost" size="sm" onClick={handleClose}>{t('actions.cancel')}</Button>
            <Button variant="primary" size="sm" onClick={handleSave} loading={createMut.isPending || updateMut.isPending}>{t('actions.save')}</Button>
          </div>

          {showCloseConfirm && (
            <div className="mt-4 p-4 bg-surface-800 border border-surface-700 rounded-lg space-y-3">
              <p className="text-sm text-surface-300">{t('confirm.unsavedChanges')}</p>
              <div className="flex justify-end gap-2">
                <Button variant="ghost" size="sm" onClick={() => setShowCloseConfirm(false)}>{t('actions.backToForm')}</Button>
                <Button variant="ghost" size="sm" onClick={closeForm}>{t('actions.discard')}</Button>
                {canSave && (
                  <Button variant="primary" size="sm" onClick={handleSave} loading={createMut.isPending || updateMut.isPending}>{t('actions.save')}</Button>
                )}
              </div>
            </div>
          )}
        </div>
      </Modal>

      <ConfirmDialog isOpen={!!deleteId} onClose={() => setDeleteId(null)} onConfirm={() => { if (deleteId) { deleteMut.mutate(deleteId); setDeleteId(null) } }} title={t('confirm.deleteTitle')} message={t('confirm.delete')} confirmLabel={t('actions.delete')} danger />
    </div>
  )
}
