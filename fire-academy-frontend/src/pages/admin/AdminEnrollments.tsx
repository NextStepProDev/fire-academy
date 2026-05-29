import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { adminApi } from '../../api/admin'
import { Button } from '../../components/ui/Button'
import { Modal } from '../../components/ui/Modal'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { Trash2, UserPlus } from 'lucide-react'
import type { EventCategory } from '../../types'

export function AdminEnrollments() {
  const { t } = useTranslation('admin')
  const queryClient = useQueryClient()
  const [category, setCategory] = useState<EventCategory>('TRAINING')
  const [selectedEventId, setSelectedEventId] = useState<string>('')
  const [isAdding, setIsAdding] = useState(false)
  const [deleteId, setDeleteId] = useState<string | null>(null)
  const [form, setForm] = useState({ firstName: '', lastName: '', email: '', phone: '' })

  const { data: events } = useQuery({
    queryKey: ['admin', 'events', category],
    queryFn: () => adminApi.getEvents(category),
  })

  const { data: enrollments, isLoading } = useQuery({
    queryKey: ['admin', 'enrollments', selectedEventId],
    queryFn: () => adminApi.getEnrollmentsByEvent(selectedEventId),
    enabled: !!selectedEventId,
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['admin', 'enrollments', selectedEventId] })

  const addMut = useMutation({
    mutationFn: () => adminApi.adminEnroll({
      eventId: selectedEventId,
      firstName: form.firstName,
      lastName: form.lastName,
      email: form.email,
      phone: form.phone,
    }),
    onSuccess: () => { invalidate(); setIsAdding(false) },
  })
  const deleteMut = useMutation({ mutationFn: adminApi.deleteEnrollment, onSuccess: invalidate })

  const selectedEvent = events?.find(e => e.id === selectedEventId)

  return (
    <div>
      <h2 className="text-xl font-semibold text-surface-100 mb-6">{t('enrollments.title')}</h2>

      <div className="flex flex-wrap gap-4 mb-6">
        <div className="flex gap-2">
          <button onClick={() => { setCategory('TRAINING'); setSelectedEventId('') }} className={`px-3 py-1.5 text-sm rounded-lg ${category === 'TRAINING' ? 'bg-primary-600 text-white' : 'bg-surface-800 text-surface-300'}`}>Treningi</button>
          <button onClick={() => { setCategory('CAMP'); setSelectedEventId('') }} className={`px-3 py-1.5 text-sm rounded-lg ${category === 'CAMP' ? 'bg-primary-600 text-white' : 'bg-surface-800 text-surface-300'}`}>Obozy</button>
          <button onClick={() => { setCategory('COURSE'); setSelectedEventId('') }} className={`px-3 py-1.5 text-sm rounded-lg ${category === 'COURSE' ? 'bg-primary-600 text-white' : 'bg-surface-800 text-surface-300'}`}>Szkolenia</button>
        </div>
        <select
          value={selectedEventId}
          onChange={e => setSelectedEventId(e.target.value)}
          className="flex-1 min-w-[200px] px-3 py-2 bg-surface-800 border border-surface-700 rounded-lg text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
        >
          <option value="">{t('enrollments.selectEvent')}</option>
          {events?.map(ev => (
            <option key={ev.id} value={ev.id}>{ev.eventTypeName} — {ev.startDate}{ev.location ? ` (${ev.location})` : ''}</option>
          ))}
        </select>
      </div>

      {selectedEventId && (
        <>
          <div className="flex justify-between items-center mb-4">
            <p className="text-sm text-surface-400">
              {selectedEvent && (
                selectedEvent.maxParticipants != null
                  ? t('enrollments.summary', { count: enrollments?.length ?? 0, max: selectedEvent.maxParticipants })
                  : t('enrollments.summaryNoLimit', { count: enrollments?.length ?? 0 })
              )}
            </p>
            <Button variant="primary" size="sm" onClick={() => { setForm({ firstName: '', lastName: '', email: '', phone: '' }); setIsAdding(true) }}>
              <UserPlus className="w-4 h-4 mr-1.5" />
              {t('actions.addEnrollment')}
            </Button>
          </div>

          {isLoading ? (
            <LoadingSpinner />
          ) : !enrollments?.length ? (
            <p className="text-surface-500">{t('enrollments.noItems')}</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-surface-800 text-left text-surface-400">
                    <th className="pb-3 pr-4">{t('enrollments.firstName')}</th>
                    <th className="pb-3 pr-4">{t('enrollments.lastName')}</th>
                    <th className="pb-3 pr-4">{t('enrollments.email')}</th>
                    <th className="pb-3 pr-4">{t('enrollments.phone')}</th>
                    <th className="pb-3 pr-4">{t('enrollments.date')}</th>
                    <th className="pb-3"></th>
                  </tr>
                </thead>
                <tbody>
                  {enrollments.map(en => (
                    <tr key={en.id} className="border-b border-surface-800/50">
                      <td className="py-3 pr-4 text-surface-100">{en.firstName}</td>
                      <td className="py-3 pr-4 text-surface-100">{en.lastName}</td>
                      <td className="py-3 pr-4 text-surface-300">{en.email}</td>
                      <td className="py-3 pr-4 text-surface-300">{en.phone}</td>
                      <td className="py-3 pr-4 text-surface-500">
                        {new Date(en.createdAt).toLocaleDateString('pl')}
                        {en.addedByAdmin && <span className="ml-2 px-1.5 py-0.5 text-xs bg-surface-800 text-surface-400 rounded">admin</span>}
                      </td>
                      <td className="py-3">
                        <button onClick={() => setDeleteId(en.id)} className="p-1 text-surface-400 hover:text-rose-400"><Trash2 className="w-4 h-4" /></button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
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
          <div className="flex justify-end gap-3">
            <Button variant="ghost" size="sm" onClick={() => setIsAdding(false)}>{t('actions.cancel')}</Button>
            <Button variant="primary" size="sm" onClick={() => addMut.mutate()} loading={addMut.isPending}>{t('actions.save')}</Button>
          </div>
        </div>
      </Modal>

      <ConfirmDialog isOpen={!!deleteId} onClose={() => setDeleteId(null)} onConfirm={() => { if (deleteId) { deleteMut.mutate(deleteId); setDeleteId(null) } }} title={t('confirm.deleteTitle')} message={t('confirm.delete')} confirmLabel={t('actions.delete')} danger />
    </div>
  )
}
