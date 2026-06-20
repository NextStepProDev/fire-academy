import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { adminApi } from '../../api/admin'
import { Button } from '../../components/ui/Button'
import { Modal } from '../../components/ui/Modal'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { useToast } from '../../context/ToastContext'
import { ChevronUp, ChevronDown, Pencil, Trash2, Plus, X } from 'lucide-react'
import type { EventCategory, EventType } from '../../types'
import clsx from 'clsx'

interface AdminEventTypesProps {
  category: EventCategory
}

export function AdminEventTypes({ category }: AdminEventTypesProps) {
  const { t } = useTranslation('admin')
  const { showToast } = useToast()
  const queryClient = useQueryClient()
  const [editItem, setEditItem] = useState<EventType | null>(null)
  const [isCreating, setIsCreating] = useState(false)
  const [deleteId, setDeleteId] = useState<string | null>(null)
  const [form, setForm] = useState({ name: '', description: '' })

  const queryKey = ['admin', 'event-types', category]
  const { data: types, isLoading } = useQuery({
    queryKey,
    queryFn: () => adminApi.getEventTypes(category),
    staleTime: 0,
  })

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey })
    queryClient.invalidateQueries({ queryKey: ['public', 'event-types', category] })
    queryClient.invalidateQueries({ queryKey: ['public', 'events', category] })
  }

  const createMut = useMutation({
    mutationFn: () => adminApi.createEventType({
      category,
      name: form.name,
      description: form.description || undefined,
    }),
    onSuccess: invalidate,
  })
  const updateMut = useMutation({
    mutationFn: (id: string) => adminApi.updateEventType(id, {
      name: form.name,
      description: form.description || undefined,
    }),
    onSuccess: invalidate,
  })
  const deleteMut = useMutation({ mutationFn: adminApi.deleteEventType, onSuccess: invalidate, onError: (e: Error) => showToast(e.message, 'error') })
  const toggleMut = useMutation({ mutationFn: adminApi.toggleEventTypeActive, onSuccess: invalidate })
  const reorderMut = useMutation({ mutationFn: ({ id, dir }: { id: string; dir: string }) => adminApi.reorderEventType(id, dir), onSuccess: invalidate })
  const thumbMut = useMutation({ mutationFn: ({ id, file }: { id: string; file: File }) => adminApi.uploadEventTypeThumbnail(id, file), onSuccess: invalidate, onError: (e: Error) => showToast(e.message, 'error') })
  const photoMut = useMutation({
    mutationFn: async ({ id, files }: { id: string; files: File[] }) => {
      for (const file of files) await adminApi.addEventTypePhoto(id, file)
    },
    onSuccess: invalidate,
    onError: (e: Error) => showToast(e.message, 'error'),
  })
  const deletePhotoMut = useMutation({ mutationFn: ({ id, photoId }: { id: string; photoId: string }) => adminApi.deleteEventTypePhoto(id, photoId), onSuccess: invalidate, onError: (e: Error) => showToast(e.message, 'error') })
  const reorderPhotoMut = useMutation({ mutationFn: ({ id, photoId, dir }: { id: string; photoId: string; dir: string }) => adminApi.reorderEventTypePhoto(id, photoId, dir), onSuccess: invalidate })

  const openCreate = () => { setForm({ name: '', description: '' }); setIsCreating(true) }
  const openEdit = (et: EventType) => {
    setForm({
      name: et.name,
      description: et.description ?? '',
    })
    setEditItem(et)
  }

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
        <h2 className="text-xl font-semibold text-surface-100">{t('eventTypes.title')}</h2>
        <Button variant="primary" size="sm" onClick={openCreate}>{t('actions.create')}</Button>
      </div>

      {!types?.length ? (
        <p className="text-surface-500">{t('eventTypes.noItems')}</p>
      ) : (
        <div className="space-y-4">
          {types.map((et, idx) => (
            <div key={et.id} className={clsx('bg-surface-900 border border-surface-800 rounded-xl p-4', !et.active && 'opacity-50')}>
              <div className="flex items-center gap-4">
                <div className="w-16 h-12 rounded bg-surface-800 flex-shrink-0 overflow-hidden">
                  {et.thumbnailUrl ? <img src={et.thumbnailUrl} alt="" loading="lazy" decoding="async" className="w-full h-full object-cover" /> : <span className="flex items-center justify-center h-full text-xs text-surface-600">–</span>}
                </div>
                <div className="flex-1 min-w-0">
                  <p className="font-medium text-surface-100 truncate">{et.name}</p>
                  {et.description && <p className="text-sm text-surface-500 truncate">{et.description}</p>}
                </div>
              </div>
              <div className="flex items-center gap-1 mt-3 flex-wrap">
                <label className="cursor-pointer">
                  <input type="file" accept="image/jpeg,image/png,image/webp" className="hidden" onChange={e => { if (e.target.files?.[0]) thumbMut.mutate({ id: et.id, file: e.target.files[0] }) }} />
                  <span className="px-2 py-1 text-xs bg-surface-800 text-surface-300 rounded hover:bg-surface-700 transition-colors">{t('eventTypes.thumbnail')}</span>
                </label>
                <button onClick={() => reorderMut.mutate({ id: et.id, dir: 'up' })} disabled={idx === 0} className="p-1 text-surface-400 hover:text-surface-200 disabled:opacity-30"><ChevronUp className="w-4 h-4" /></button>
                <button onClick={() => reorderMut.mutate({ id: et.id, dir: 'down' })} disabled={idx === types.length - 1} className="p-1 text-surface-400 hover:text-surface-200 disabled:opacity-30"><ChevronDown className="w-4 h-4" /></button>
                <button onClick={() => toggleMut.mutate(et.id)} className={clsx('px-2 py-1 text-xs rounded', et.active ? 'bg-green-900/30 text-green-400' : 'bg-surface-800 text-surface-500')}>
                  {et.active ? t('actions.deactivate') : t('actions.activate')}
                </button>
                <button onClick={() => openEdit(et)} className="p-1 text-surface-400 hover:text-primary-400"><Pencil className="w-4 h-4" /></button>
                <button onClick={() => setDeleteId(et.id)} className="p-1 text-surface-400 hover:text-rose-400"><Trash2 className="w-4 h-4" /></button>
              </div>
              {/* Gallery photos */}
              <div className="flex flex-wrap gap-2 mt-3">
                {et.photos.map((p, pi) => (
                  <div key={p.id} className="relative w-20 h-20 rounded bg-surface-800 overflow-hidden group">
                    <img src={p.url} alt="" loading="lazy" decoding="async" className="w-full h-full object-cover" />
                    <button onClick={() => deletePhotoMut.mutate({ id: et.id, photoId: p.id })} className="absolute top-0.5 right-0.5 p-1 rounded-full bg-black/70 text-rose-400 hover:text-rose-300 hover:bg-black/90 transition-colors">
                      <X className="w-4 h-4" />
                    </button>
                    <div className="absolute bottom-0 inset-x-0 flex justify-center gap-1 p-0.5 bg-black/50 opacity-0 group-hover:opacity-100 transition-opacity">
                      <button onClick={() => reorderPhotoMut.mutate({ id: et.id, photoId: p.id, dir: 'up' })} disabled={pi === 0} className="p-0.5 text-white disabled:opacity-30"><ChevronUp className="w-3.5 h-3.5" /></button>
                      <button onClick={() => reorderPhotoMut.mutate({ id: et.id, photoId: p.id, dir: 'down' })} disabled={pi === et.photos.length - 1} className="p-0.5 text-white disabled:opacity-30"><ChevronDown className="w-3.5 h-3.5" /></button>
                    </div>
                  </div>
                ))}
                <label className="w-20 h-20 rounded border-2 border-dashed border-surface-700 flex items-center justify-center cursor-pointer hover:border-primary-500 transition-colors">
                  <input type="file" accept="image/jpeg,image/png,image/webp" multiple className="hidden" onChange={e => { if (e.target.files?.length) photoMut.mutate({ id: et.id, files: Array.from(e.target.files) }) }} />
                  <Plus className="w-5 h-5 text-surface-500" />
                </label>
              </div>
            </div>
          ))}
        </div>
      )}

      <Modal
        isOpen={isCreating || !!editItem}
        onClose={() => { setIsCreating(false); setEditItem(null) }}
        title={editItem ? t('eventTypes.editTitle') : t('eventTypes.createTitle')}
      >
        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-surface-300 mb-1">{t('eventTypes.name')}</label>
            <input value={form.name} onChange={e => setForm(f => ({ ...f, name: e.target.value }))} className="w-full px-3 py-2 bg-surface-800 border border-surface-700 rounded-lg text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500" />
          </div>
          <div>
            <label className="block text-sm font-medium text-surface-300 mb-1">{t('eventTypes.description')}</label>
            <textarea value={form.description} onChange={e => setForm(f => ({ ...f, description: e.target.value }))} rows={12} className="w-full px-3 py-2 bg-surface-800 border border-surface-700 rounded-lg text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500 resize-y" />
          </div>
          <div className="flex justify-end gap-3">
            <Button variant="ghost" size="sm" onClick={() => { setIsCreating(false); setEditItem(null) }}>{t('actions.cancel')}</Button>
            <Button variant="primary" size="sm" onClick={handleSave} loading={createMut.isPending || updateMut.isPending}>{t('actions.save')}</Button>
          </div>
        </div>
      </Modal>

      <ConfirmDialog isOpen={!!deleteId} onClose={() => setDeleteId(null)} onConfirm={() => { if (deleteId) { deleteMut.mutate(deleteId); setDeleteId(null) } }} title={t('confirm.deleteTitle')} message={t('confirm.delete')} confirmLabel={t('actions.delete')} danger />
    </div>
  )
}
