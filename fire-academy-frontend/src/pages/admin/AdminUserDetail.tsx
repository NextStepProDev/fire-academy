import { useMemo, useState, type FormEvent, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, BadgeCheck, Bell, BellOff, CalendarPlus, Trash2, CheckCircle, XCircle, Mail, Phone, KeyRound } from 'lucide-react'
import { adminApi } from '../../api/admin'
import { Avatar } from '../../components/ui/Avatar'
import { Button } from '../../components/ui/Button'
import { Modal } from '../../components/ui/Modal'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { useToast } from '../../context/ToastContext'
import type { EventCategory, EventInstance, UserEnrollment } from '../../types'

const categoryLabelKey: Record<EventCategory, string> = {
  TRAINING: 'archive.categoryTraining',
  CAMP: 'archive.categoryCamp',
  COURSE: 'archive.categoryCourse',
}

function formatSchedule(en: UserEnrollment): string {
  const datePart = en.endDate && en.endDate !== en.startDate
    ? `${en.startDate} – ${en.endDate}`
    : en.startDate
  const timePart = en.startTime ? ` · ${en.startTime}${en.endTime ? `–${en.endTime}` : ''}` : ''
  return datePart + timePart
}

function isUpcoming(event: EventInstance): boolean {
  const today = new Date().toISOString().split('T')[0]
  return (event.endDate ?? event.startDate) >= today
}

export function AdminUserDetail({ userId, onBack }: { userId: string; onBack: () => void }) {
  const { t } = useTranslation('admin')
  const { showToast } = useToast()
  const queryClient = useQueryClient()

  const [toDelete, setToDelete] = useState<UserEnrollment | null>(null)
  const [addOpen, setAddOpen] = useState(false)
  const [selectedEventId, setSelectedEventId] = useState('')
  const [phone, setPhone] = useState('')
  const [note, setNote] = useState('')

  const userQuery = useQuery({
    queryKey: ['admin-user', userId],
    queryFn: () => adminApi.getUser(userId),
  })

  const trainingsQuery = useQuery({ queryKey: ['admin', 'events', 'TRAINING'], queryFn: () => adminApi.getEvents('TRAINING'), enabled: addOpen })
  const campsQuery = useQuery({ queryKey: ['admin', 'events', 'CAMP'], queryFn: () => adminApi.getEvents('CAMP'), enabled: addOpen })
  const coursesQuery = useQuery({ queryKey: ['admin', 'events', 'COURSE'], queryFn: () => adminApi.getEvents('COURSE'), enabled: addOpen })

  const eventOptions = useMemo(() => {
    const tag = (events: EventInstance[] | undefined, cat: EventCategory) =>
      (events ?? []).filter(e => e.active && isUpcoming(e)).map(e => ({ ...e, category: cat }))
    return [
      ...tag(trainingsQuery.data, 'TRAINING'),
      ...tag(campsQuery.data, 'CAMP'),
      ...tag(coursesQuery.data, 'COURSE'),
    ].sort((a, b) => a.startDate.localeCompare(b.startDate))
  }, [trainingsQuery.data, campsQuery.data, coursesQuery.data])

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['admin-user', userId] })

  const deleteMutation = useMutation({
    mutationFn: ({ id, notify }: { id: string; notify: boolean }) => adminApi.deleteEnrollment(id, notify),
    onSuccess: () => {
      showToast(t('users.detail.removeEnrollmentSuccess'))
      setToDelete(null)
      invalidate()
    },
    onError: (e: Error) => showToast(e.message, 'error'),
  })

  const addMutation = useMutation({
    mutationFn: () => {
      const u = userQuery.data!
      return adminApi.adminEnroll({
        eventId: selectedEventId,
        firstName: u.firstName,
        lastName: u.lastName,
        email: u.email,
        phone: phone.trim() || undefined,
        note: note.trim() || undefined,
      })
    },
    onSuccess: () => {
      showToast(t('users.detail.addEnrollmentSuccess'))
      setAddOpen(false)
      setSelectedEventId('')
      setNote('')
      invalidate()
    },
    onError: (e: Error) => showToast(e.message, 'error'),
  })

  if (userQuery.isLoading) return <LoadingSpinner />
  if (userQuery.isError || !userQuery.data) {
    return (
      <div>
        <BackButton onBack={onBack} label={t('users.detail.back')} />
        <p className="text-red-400 text-sm mt-4">{t('users.detail.loadError')}</p>
      </div>
    )
  }

  const user = userQuery.data

  const openAdd = () => {
    setPhone(user.phone ?? '')
    setNote('')
    setSelectedEventId('')
    setAddOpen(true)
  }

  const renderEnrollment = (en: UserEnrollment) => (
    <div key={en.id} className="flex items-start justify-between gap-3 bg-surface-800/50 border border-surface-800 rounded-lg px-4 py-3">
      <div className="min-w-0">
        <div className="flex items-center gap-2">
          <span className="px-2 py-0.5 text-xs rounded-full bg-primary-500/15 text-primary-300">
            {t(categoryLabelKey[en.category])}
          </span>
          <p className="font-medium text-surface-100 truncate">{en.eventName}</p>
          {en.addedByAdmin && (
            <span className="px-1.5 py-0.5 text-xs bg-surface-800 text-surface-400 rounded">admin</span>
          )}
        </div>
        <p className="text-sm text-surface-400 mt-0.5">{formatSchedule(en)}{en.location ? ` · ${en.location}` : ''}</p>
        {en.note && <p className="text-sm text-surface-500 mt-1 whitespace-pre-wrap">{en.note}</p>}
      </div>
      <Button
        variant="danger"
        size="sm"
        aria-label={en.past ? t('users.detail.removeArchiveTitle') : t('users.detail.removeEnrollmentTitle')}
        onClick={() => setToDelete(en)}
      >
        <Trash2 className="w-4 h-4" />
      </Button>
    </div>
  )

  return (
    <div>
      <BackButton onBack={onBack} label={t('users.detail.back')} />

      {/* Profile header */}
      <div className="flex items-center gap-4 mt-4 mb-6">
        <Avatar src={user.avatarUrl} name={`${user.firstName} ${user.lastName}`} className="w-16 h-16" textClassName="text-xl" />
        <div>
          <h2 className="text-xl font-semibold text-surface-100">{user.firstName} {user.lastName}</h2>
          <p className="text-surface-400 text-sm flex items-center gap-1"><Mail className="w-4 h-4" /> {user.email}</p>
          {user.role === 'ADMIN' && (
            <span className="inline-flex items-center gap-1 mt-1 text-xs px-2 py-1 rounded-full bg-primary-500/15 text-primary-300">
              <BadgeCheck className="w-3.5 h-3.5" />
              {user.superAdmin ? t('users.superAdmin') : t('users.admin')}
            </span>
          )}
        </div>
      </div>

      {/* Profile details (read-only) */}
      <div className="bg-surface-900 border border-surface-800 rounded-xl p-6 mb-8 grid grid-cols-1 sm:grid-cols-2 gap-4 text-sm">
        <ProfileRow icon={<Phone className="w-4 h-4" />} label={t('users.phone')} value={user.phone ?? '—'} />
        <ProfileRow
          icon={user.emailVerified ? <CheckCircle className="w-4 h-4 text-green-400" /> : <XCircle className="w-4 h-4 text-amber-400" />}
          label={t('users.detail.emailVerified')}
          value={user.emailVerified ? t('users.detail.yes') : t('users.detail.no')}
        />
        <ProfileRow
          icon={user.emailNotificationsEnabled ? <Bell className="w-4 h-4 text-green-400" /> : <BellOff className="w-4 h-4 text-surface-500" />}
          label={t('users.detail.notifications')}
          value={user.emailNotificationsEnabled ? t('users.detail.enabled') : t('users.detail.disabled')}
        />
        <ProfileRow icon={<KeyRound className="w-4 h-4" />} label={t('users.detail.accountType')} value={user.oauthLinked ? 'Google' : (user.hasPassword ? t('users.detail.password') : '—')} />
        <ProfileRow label={t('users.created')} value={new Date(user.createdAt).toLocaleDateString('pl-PL')} />
      </div>

      {/* Current enrollments */}
      <div className="flex items-center justify-between mb-3">
        <h3 className="text-lg font-semibold text-surface-100">{t('users.detail.currentTitle')}</h3>
        <Button variant="primary" size="sm" onClick={openAdd}>
          <CalendarPlus className="w-4 h-4 mr-2" />
          {t('users.detail.addToEvent')}
        </Button>
      </div>
      <div className="space-y-2 mb-8">
        {user.currentEnrollments.length === 0
          ? <p className="text-surface-500 text-sm">{t('users.detail.noCurrent')}</p>
          : user.currentEnrollments.map(renderEnrollment)}
      </div>

      {/* Archive */}
      <h3 className="text-lg font-semibold text-surface-100 mb-3">{t('users.detail.archiveTitle')}</h3>
      <div className="space-y-2">
        {user.pastEnrollments.length === 0
          ? <p className="text-surface-500 text-sm">{t('users.detail.noArchive')}</p>
          : user.pastEnrollments.map(renderEnrollment)}
      </div>

      {/* Add-to-event modal */}
      <Modal isOpen={addOpen} onClose={() => setAddOpen(false)} title={t('users.detail.addToEvent')}>
        <form onSubmit={(e: FormEvent) => { e.preventDefault(); addMutation.mutate() }} className="space-y-4">
          <div>
            <label className="block text-sm text-surface-300 mb-1">{t('users.detail.event')}</label>
            <select
              value={selectedEventId}
              required
              onChange={e => setSelectedEventId(e.target.value)}
              className="w-full px-4 py-2.5 bg-surface-800 border border-surface-700 rounded-lg text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
            >
              <option value="">{t('users.detail.selectEvent')}</option>
              {eventOptions.map(ev => (
                <option key={ev.id} value={ev.id}>
                  {t(categoryLabelKey[ev.category])} · {ev.eventTypeName} — {ev.startDate}
                </option>
              ))}
            </select>
            {addOpen && eventOptions.length === 0 && !trainingsQuery.isFetching && (
              <p className="text-xs text-surface-500 mt-1">{t('users.detail.noUpcomingEvents')}</p>
            )}
          </div>
          <div>
            <label className="block text-sm text-surface-300 mb-1">{t('users.detail.phoneOptional')}</label>
            <input
              type="tel"
              value={phone}
              onChange={e => setPhone(e.target.value)}
              className="w-full px-4 py-2.5 bg-surface-800 border border-surface-700 rounded-lg text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
            />
            <p className="text-xs text-surface-500 mt-1">{t('users.detail.phoneHint')}</p>
          </div>
          <div>
            <label className="block text-sm text-surface-300 mb-1">{t('users.detail.note')}</label>
            <textarea
              value={note}
              rows={3}
              onChange={e => setNote(e.target.value)}
              className="w-full px-4 py-2.5 bg-surface-800 border border-surface-700 rounded-lg text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500 resize-y"
            />
          </div>
          <div className="flex justify-end gap-3">
            <Button type="button" variant="ghost" size="sm" onClick={() => setAddOpen(false)}>{t('users.cancel')}</Button>
            <Button type="submit" variant="primary" size="sm" loading={addMutation.isPending} disabled={!selectedEventId}>
              {t('users.detail.addConfirm')}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Delete enrollment confirm — notify only for upcoming (non-archive) */}
      <ConfirmDialog
        isOpen={toDelete !== null}
        onClose={() => setToDelete(null)}
        onConfirm={() => toDelete && deleteMutation.mutate({ id: toDelete.id, notify: !toDelete.past })}
        title={toDelete?.past ? t('users.detail.removeArchiveTitle') : t('users.detail.removeEnrollmentTitle')}
        message={toDelete
          ? (toDelete.past ? t('users.detail.removeArchiveMessage', { event: toDelete.eventName }) : t('users.detail.removeEnrollmentMessage', { event: toDelete.eventName }))
          : ''}
        confirmLabel={toDelete?.past ? t('users.detail.removeArchiveConfirm') : t('users.detail.removeEnrollmentConfirm')}
        danger
        loading={deleteMutation.isPending}
      />
    </div>
  )
}

function BackButton({ onBack, label }: { onBack: () => void; label: string }) {
  return (
    <button onClick={onBack} className="inline-flex items-center gap-1 text-sm text-surface-400 hover:text-surface-200">
      <ArrowLeft className="w-4 h-4" /> {label}
    </button>
  )
}

function ProfileRow({ icon, label, value }: { icon?: ReactNode; label: string; value: string }) {
  return (
    <div className="flex items-center gap-2">
      {icon && <span className="text-surface-500">{icon}</span>}
      <span className="text-surface-400">{label}:</span>
      <span className="text-surface-100">{value}</span>
    </div>
  )
}
