import { useState, Fragment } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { adminApi } from '../../api/admin'
import { Button } from '../../components/ui/Button'
import { Modal } from '../../components/ui/Modal'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { Pencil, Trash2, UserPlus, ChevronDown, ChevronRight, MessageSquare } from 'lucide-react'
import type { EventCategory, EventInstance } from '../../types'
import clsx from 'clsx'

interface AdminEventsProps {
  category: EventCategory
}

function EventCard({
  event,
  onEdit,
  onDelete,
  onToggleActive,
}: {
  event: EventInstance
  onEdit: (ev: EventInstance) => void
  onDelete: (id: string) => void
  onToggleActive: (id: string) => void
}) {
  const { t } = useTranslation('admin')
  const queryClient = useQueryClient()
  const [expanded, setExpanded] = useState(false)
  const [expandedNote, setExpandedNote] = useState<string | null>(null)
  const [isAdding, setIsAdding] = useState(false)
  const [enrollDeleteId, setEnrollDeleteId] = useState<string | null>(null)
  const [form, setForm] = useState({ firstName: '', lastName: '', email: '', phone: '', note: '' })

  const { data: enrollments } = useQuery({
    queryKey: ['admin', 'enrollments', event.id],
    queryFn: () => adminApi.getEnrollmentsByEvent(event.id),
    staleTime: 0,
  })

  const enrollCount = enrollments?.length ?? 0

  const addMut = useMutation({
    mutationFn: () => adminApi.adminEnroll({
      eventId: event.id,
      firstName: form.firstName,
      lastName: form.lastName,
      email: form.email,
      phone: form.phone.replace(/\s/g, ''),
      note: form.note.trim() || undefined,
    }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'enrollments', event.id] })
      queryClient.invalidateQueries({ queryKey: ['admin', 'events'] })
      queryClient.invalidateQueries({ queryKey: ['public', 'events'] })
      setIsAdding(false)
    },
  })

  const deleteMut = useMutation({
    mutationFn: adminApi.deleteEnrollment,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'enrollments', event.id] })
      queryClient.invalidateQueries({ queryKey: ['admin', 'events'] })
      queryClient.invalidateQueries({ queryKey: ['public', 'events'] })
    },
  })

  return (
    <div className={clsx('bg-surface-900 border border-surface-800 rounded-xl overflow-hidden', !event.active && 'opacity-50')}>
      <div className="px-4 py-4 flex items-start gap-4 cursor-pointer hover:bg-surface-800/30 transition-colors" onClick={() => setExpanded(!expanded)}>
        <div className="mt-0.5 p-0.5 text-surface-400 shrink-0">
          {expanded ? <ChevronDown className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />}
        </div>

        <div className="flex-1 min-w-0">
          <p className="font-medium text-surface-100">{event.eventTypeName}</p>
          <p className="text-sm text-surface-400">
            {event.startDate}{event.endDate ? ` – ${event.endDate}` : ''}
            {event.startTime ? ` · ${event.startTime}${event.endTime ? ` – ${event.endTime}` : ''}` : ''}
            {event.location ? ` · ${event.location}` : ''}
          </p>
          <p className="text-sm text-surface-500">
            {event.price != null && `${event.price} PLN · `}
            {t('events.enrolled')}: {enrollCount}
            {event.maxParticipants != null && ` / ${event.maxParticipants}`}
          </p>
        </div>

        <div className="flex items-center gap-1 shrink-0" onClick={e => e.stopPropagation()}>
          <button onClick={() => onToggleActive(event.id)} className={clsx('px-2 py-1 text-xs rounded', event.active ? 'bg-green-900/30 text-green-400' : 'bg-surface-800 text-surface-500')}>
            {event.active ? t('actions.deactivate') : t('actions.activate')}
          </button>
          <button onClick={() => onEdit(event)} className="p-1 text-surface-400 hover:text-primary-400"><Pencil className="w-4 h-4" /></button>
          <button onClick={() => onDelete(event.id)} className="p-1 text-surface-400 hover:text-rose-400"><Trash2 className="w-4 h-4" /></button>
        </div>
      </div>

      {expanded && (
        <div className="border-t border-surface-800 px-5 py-3">
          <div className="flex justify-end mb-3">
            <Button variant="primary" size="sm" onClick={() => { setForm({ firstName: '', lastName: '', email: '', phone: '', note: '' }); setIsAdding(true) }}>
              <UserPlus className="w-4 h-4 mr-1.5" />
              {t('actions.addEnrollment')}
            </Button>
          </div>
          {!enrollCount ? (
            <p className="text-sm text-surface-500 py-2">{t('enrollments.noItems')}</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-surface-800 text-left text-surface-400">
                    <th className="pb-2 pr-4">{t('enrollments.firstName')}</th>
                    <th className="pb-2 pr-4">{t('enrollments.lastName')}</th>
                    <th className="pb-2 pr-4">{t('enrollments.email')}</th>
                    <th className="pb-2 pr-4">{t('enrollments.phone')}</th>
                    <th className="pb-2 pr-4">{t('enrollments.date')}</th>
                    <th className="pb-2"></th>
                  </tr>
                </thead>
                <tbody>
                  {enrollments!.map(en => (
                    <Fragment key={en.id}>
                      <tr className="border-b border-surface-800/50">
                        <td className="py-2.5 pr-4 text-surface-100">{en.firstName}</td>
                        <td className="py-2.5 pr-4 text-surface-100">{en.lastName}</td>
                        <td className="py-2.5 pr-4 text-surface-300">{en.email}</td>
                        <td className="py-2.5 pr-4 text-surface-300">{en.phone}</td>
                        <td className="py-2.5 pr-4 text-surface-500">
                          {new Date(en.createdAt).toLocaleDateString('pl')}
                          {en.addedByAdmin && <span className="ml-2 px-1.5 py-0.5 text-xs bg-surface-800 text-surface-400 rounded">admin</span>}
                        </td>
                        <td className="py-2.5">
                          <div className="flex items-center gap-1">
                            {en.note && (
                              <button
                                onClick={() => setExpandedNote(expandedNote === en.id ? null : en.id)}
                                className={`p-1 ${expandedNote === en.id ? 'text-primary-400' : 'text-surface-400 hover:text-primary-400'}`}
                                title={t('enrollments.note')}
                              >
                                <MessageSquare className="w-4 h-4" />
                              </button>
                            )}
                            <button onClick={() => setEnrollDeleteId(en.id)} className="p-1 text-surface-400 hover:text-rose-400">
                              <Trash2 className="w-4 h-4" />
                            </button>
                          </div>
                        </td>
                      </tr>
                      {expandedNote === en.id && en.note && (
                        <tr className="border-b border-surface-800/50">
                          <td colSpan={6} className="pb-3 pt-1 px-1">
                            <div className="bg-surface-800 rounded-lg p-3 text-sm text-surface-300 whitespace-pre-wrap">
                              {en.note}
                            </div>
                          </td>
                        </tr>
                      )}
                    </Fragment>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      <Modal isOpen={isAdding} onClose={() => setIsAdding(false)} title={t('actions.addEnrollment')}>
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-surface-300 mb-1">{t('enrollments.firstName')}</label>
              <input value={form.firstName} onChange={e => setForm(f => ({ ...f, firstName: e.target.value }))} className="w-full px-3 py-2 bg-surface-800 border border-surface-700 rounded-lg text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500" />
            </div>
            <div>
              <label className="block text-sm font-medium text-surface-300 mb-1">{t('enrollments.lastName')}</label>
              <input value={form.lastName} onChange={e => setForm(f => ({ ...f, lastName: e.target.value }))} className="w-full px-3 py-2 bg-surface-800 border border-surface-700 rounded-lg text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500" />
            </div>
          </div>
          <div>
            <label className="block text-sm font-medium text-surface-300 mb-1">{t('enrollments.email')}</label>
            <input type="email" value={form.email} onChange={e => setForm(f => ({ ...f, email: e.target.value }))} className="w-full px-3 py-2 bg-surface-800 border border-surface-700 rounded-lg text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500" />
          </div>
          <div>
            <label className="block text-sm font-medium text-surface-300 mb-1">{t('enrollments.phone')}</label>
            <input type="tel" value={form.phone} onChange={e => setForm(f => ({ ...f, phone: e.target.value }))} className="w-full px-3 py-2 bg-surface-800 border border-surface-700 rounded-lg text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500" />
          </div>
          <div>
            <label className="block text-sm font-medium text-surface-300 mb-1">
              {t('enrollments.note')} <span className="text-surface-500 font-normal">({t('enrollments.optional')})</span>
            </label>
            <textarea value={form.note} onChange={e => setForm(f => ({ ...f, note: e.target.value }))} rows={3} className="w-full px-3 py-2 bg-surface-800 border border-surface-700 rounded-lg text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none" />
          </div>
          <div className="flex justify-end gap-3">
            <Button variant="ghost" size="sm" onClick={() => setIsAdding(false)}>{t('actions.cancel')}</Button>
            <Button variant="primary" size="sm" onClick={() => addMut.mutate()} loading={addMut.isPending}>{t('actions.save')}</Button>
          </div>
        </div>
      </Modal>

      <ConfirmDialog
        isOpen={!!enrollDeleteId}
        onClose={() => setEnrollDeleteId(null)}
        onConfirm={() => { if (enrollDeleteId) { deleteMut.mutate(enrollDeleteId); setEnrollDeleteId(null) } }}
        title={t('confirm.deleteTitle')}
        message={t('confirm.delete')}
        confirmLabel={t('actions.delete')}
        danger
      />
    </div>
  )
}

export function AdminEvents({ category }: AdminEventsProps) {
  const { t } = useTranslation('admin')
  const queryClient = useQueryClient()
  const [editItem, setEditItem] = useState<EventInstance | null>(null)
  const [isCreating, setIsCreating] = useState(false)
  const [deleteId, setDeleteId] = useState<string | null>(null)
  const [form, setForm] = useState({ eventTypeName: '', description: '', startDate: '', endDate: '', startTime: '', endTime: '', location: '', price: '', maxParticipants: '' })

  const queryKey = ['admin', 'events', category]
  const { data: events, isLoading } = useQuery({ queryKey, queryFn: () => adminApi.getEvents(category), staleTime: 0 })
  const { data: eventTypes } = useQuery({ queryKey: ['admin', 'event-types', category], queryFn: () => adminApi.getEventTypes(category), staleTime: 0 })

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey })
    queryClient.invalidateQueries({ queryKey: ['admin', 'event-types', category] })
    queryClient.invalidateQueries({ queryKey: ['public', 'events', category] })
    queryClient.invalidateQueries({ queryKey: ['public', 'event-types', category] })
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

  const closeForm = () => { setIsCreating(false); setEditItem(null) }

  const handleSave = async () => {
    if (editItem) {
      await updateMut.mutateAsync(editItem.id)
      setEditItem(null)
    } else {
      await createMut.mutateAsync()
      setIsCreating(false)
    }
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
            <EventCard
              key={ev.id}
              event={ev}
              onEdit={openEdit}
              onDelete={id => setDeleteId(id)}
              onToggleActive={id => toggleMut.mutate(id)}
            />
          ))}
        </div>
      )}

      <Modal
        isOpen={isCreating || !!editItem}
        onClose={closeForm}
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
              <input type="date" value={form.startDate} min={new Date().toISOString().split('T')[0]} onChange={e => setForm(f => ({ ...f, startDate: e.target.value }))} className="w-full px-3 py-2 bg-surface-800 border border-surface-700 rounded-lg text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500" />
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
            <Button variant="ghost" size="sm" onClick={closeForm}>{t('actions.cancel')}</Button>
            <Button variant="primary" size="sm" onClick={handleSave} loading={createMut.isPending || updateMut.isPending}>{t('actions.save')}</Button>
          </div>
        </div>
      </Modal>

      <ConfirmDialog isOpen={!!deleteId} onClose={() => setDeleteId(null)} onConfirm={() => { if (deleteId) { deleteMut.mutate(deleteId); setDeleteId(null) } }} title={t('confirm.deleteTitle')} message={t('confirm.delete')} confirmLabel={t('actions.delete')} danger />
    </div>
  )
}
