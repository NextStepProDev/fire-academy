import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { adminApi } from '../../api/admin'
import { Button } from '../../components/ui/Button'
import { Modal } from '../../components/ui/Modal'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { ChevronUp, ChevronDown, Pencil, Trash2, User } from 'lucide-react'
import type { EventCategory, Instructor } from '../../types'
import clsx from 'clsx'

const ALL_CATEGORIES: { key: EventCategory; labelKey: string }[] = [
  { key: 'TRAINING', labelKey: 'kadra.categoryTraining' },
  { key: 'CAMP', labelKey: 'kadra.categoryCamp' },
  { key: 'COURSE', labelKey: 'kadra.categoryCourse' },
]

export function AdminInstructors() {
  const { t } = useTranslation('admin')
  const queryClient = useQueryClient()
  const [editItem, setEditItem] = useState<Instructor | null>(null)
  const [isCreating, setIsCreating] = useState(false)
  const [deleteId, setDeleteId] = useState<string | null>(null)
  const [form, setForm] = useState({ firstName: '', lastName: '', bio: '', categories: [] as EventCategory[] })

  const { data: instructors, isLoading } = useQuery({
    queryKey: ['admin', 'instructors'],
    queryFn: adminApi.getInstructors,
    staleTime: 0,
  })

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'instructors'] })
    queryClient.invalidateQueries({ queryKey: ['public', 'instructors'] })
  }

  const createMut = useMutation({ mutationFn: (d: typeof form) => adminApi.createInstructor(d), onSuccess: invalidate })
  const updateMut = useMutation({ mutationFn: ({ id, ...d }: typeof form & { id: string }) => adminApi.updateInstructor(id, d), onSuccess: invalidate })
  const deleteMut = useMutation({ mutationFn: adminApi.deleteInstructor, onSuccess: invalidate })
  const toggleMut = useMutation({ mutationFn: adminApi.toggleInstructorActive, onSuccess: invalidate })
  const reorderMut = useMutation({ mutationFn: ({ id, dir }: { id: string; dir: string }) => adminApi.reorderInstructor(id, dir), onSuccess: invalidate })
  const photoMut = useMutation({ mutationFn: ({ id, file }: { id: string; file: File }) => adminApi.uploadInstructorPhoto(id, file), onSuccess: invalidate })

  const openCreate = () => { setForm({ firstName: '', lastName: '', bio: '', categories: [] }); setIsCreating(true) }
  const openEdit = (i: Instructor) => { setForm({ firstName: i.firstName, lastName: i.lastName, bio: i.bio ?? '', categories: [...i.categories] }); setEditItem(i) }

  const toggleCategory = (cat: EventCategory) => {
    setForm(f => ({
      ...f,
      categories: f.categories.includes(cat)
        ? f.categories.filter(c => c !== cat)
        : [...f.categories, cat],
    }))
  }

  const handleSave = async () => {
    if (form.categories.length === 0) return
    if (editItem) {
      await updateMut.mutateAsync({ id: editItem.id, ...form })
      setEditItem(null)
    } else {
      await createMut.mutateAsync(form)
      setIsCreating(false)
    }
  }

  if (isLoading) return <LoadingSpinner />

  return (
    <div>
      <div className="flex justify-between items-center mb-6">
        <h2 className="text-xl font-semibold text-surface-100">{t('kadra.title')}</h2>
        <Button variant="primary" size="sm" onClick={openCreate}>{t('actions.create')}</Button>
      </div>

      {!instructors?.length ? (
        <p className="text-surface-500">{t('kadra.noItems')}</p>
      ) : (
        <div className="space-y-3">
          {instructors.map((instr, idx) => (
            <div key={instr.id} className="bg-surface-900 border border-surface-800 rounded-xl p-4">
              <div className="flex items-center gap-4">
                <div className={clsx('w-12 h-12 rounded-full bg-surface-800 flex items-center justify-center overflow-hidden flex-shrink-0', !instr.active && 'opacity-50')}>
                  {instr.photoUrl ? (
                    <img src={instr.photoUrl} alt="" className="w-full h-full object-cover" />
                  ) : (
                    <User className="w-6 h-6 text-surface-600" />
                  )}
                </div>
                <div className={clsx('flex-1 min-w-0', !instr.active && 'opacity-50')}>
                  <p className="font-medium text-surface-100 truncate">{instr.firstName} {instr.lastName}</p>
                  <div className="flex gap-1 mt-1">
                    {instr.categories.map(cat => (
                      <span key={cat} className="px-1.5 py-0.5 text-[10px] rounded bg-primary-900/30 text-primary-400">
                        {t(`kadra.category${cat.charAt(0) + cat.slice(1).toLowerCase()}`)}
                      </span>
                    ))}
                  </div>
                </div>
              </div>
              <div className="flex items-center gap-1 mt-3 flex-wrap">
                <label className="cursor-pointer">
                  <input
                    type="file"
                    accept="image/*"
                    className="hidden"
                    onChange={e => { if (e.target.files?.[0]) photoMut.mutate({ id: instr.id, file: e.target.files[0] }) }}
                  />
                  <span className="px-2 py-1 text-xs bg-surface-800 text-surface-300 rounded hover:bg-surface-700 transition-colors">{t('actions.uploadPhoto')}</span>
                </label>
                <button onClick={() => reorderMut.mutate({ id: instr.id, dir: 'up' })} disabled={idx === 0} className="p-1 text-surface-400 hover:text-surface-200 disabled:opacity-30"><ChevronUp className="w-4 h-4" /></button>
                <button onClick={() => reorderMut.mutate({ id: instr.id, dir: 'down' })} disabled={idx === instructors.length - 1} className="p-1 text-surface-400 hover:text-surface-200 disabled:opacity-30"><ChevronDown className="w-4 h-4" /></button>
                <button onClick={() => toggleMut.mutate(instr.id)} className={clsx('px-2 py-1 text-xs rounded', instr.active ? 'bg-green-900/30 text-green-400' : 'bg-surface-800 text-surface-500')}>
                  {instr.active ? t('actions.deactivate') : t('actions.activate')}
                </button>
                <button onClick={() => openEdit(instr)} className="p-1 text-surface-400 hover:text-primary-400"><Pencil className="w-4 h-4" /></button>
                <button onClick={() => setDeleteId(instr.id)} className="p-1 text-surface-400 hover:text-rose-400"><Trash2 className="w-4 h-4" /></button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Create / Edit Modal */}
      <Modal
        isOpen={isCreating || !!editItem}
        onClose={() => { setIsCreating(false); setEditItem(null) }}
        title={editItem ? t('kadra.editTitle') : t('kadra.createTitle')}
      >
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-surface-300 mb-1">{t('kadra.firstName')}</label>
              <input value={form.firstName} onChange={e => setForm(f => ({ ...f, firstName: e.target.value }))} className="w-full px-3 py-2 bg-surface-800 border border-surface-700 rounded-lg text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500" />
            </div>
            <div>
              <label className="block text-sm font-medium text-surface-300 mb-1">{t('kadra.lastName')}</label>
              <input value={form.lastName} onChange={e => setForm(f => ({ ...f, lastName: e.target.value }))} className="w-full px-3 py-2 bg-surface-800 border border-surface-700 rounded-lg text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500" />
            </div>
          </div>
          <div>
            <label className="block text-sm font-medium text-surface-300 mb-1">{t('kadra.bio')}</label>
            <textarea value={form.bio} onChange={e => setForm(f => ({ ...f, bio: e.target.value }))} rows={4} className="w-full px-3 py-2 bg-surface-800 border border-surface-700 rounded-lg text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none" />
          </div>
          <div>
            <label className="block text-sm font-medium text-surface-300 mb-2">{t('kadra.categories')}</label>
            <div className="flex gap-3">
              {ALL_CATEGORIES.map(({ key, labelKey }) => (
                <label key={key} className="flex items-center gap-2 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={form.categories.includes(key)}
                    onChange={() => toggleCategory(key)}
                    className="w-4 h-4 rounded border-surface-600 bg-surface-800 text-primary-500 focus:ring-primary-500 focus:ring-offset-0"
                  />
                  <span className="text-sm text-surface-200">{t(labelKey)}</span>
                </label>
              ))}
            </div>
            {(isCreating || editItem) && form.categories.length === 0 && (
              <p className="text-xs text-rose-400 mt-1">{t('kadra.categoriesRequired')}</p>
            )}
          </div>
          <div className="flex justify-end gap-3">
            <Button variant="ghost" size="sm" onClick={() => { setIsCreating(false); setEditItem(null) }}>{t('actions.cancel')}</Button>
            <Button variant="primary" size="sm" onClick={handleSave} loading={createMut.isPending || updateMut.isPending} disabled={form.categories.length === 0}>{t('actions.save')}</Button>
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
