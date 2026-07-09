import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useParams, useNavigate, Navigate } from 'react-router-dom'
import clsx from 'clsx'
import { AdminInstructors } from './admin/AdminInstructors'
import { AdminEventTypes } from './admin/AdminEventTypes'
import { AdminEvents } from './admin/AdminEvents'
import { AdminTrainingSlots } from './admin/AdminTrainingSlots'
import { AdminTrainingParticipants } from './admin/AdminTrainingParticipants'
import { AdminCancelledSessions } from './admin/AdminCancelledSessions'
import { AdminTrainingHolidays } from './admin/AdminTrainingHolidays'
import { AdminTrainingRefunds } from './admin/AdminTrainingRefunds'
import { AdminTrainingUserDetail } from './admin/AdminTrainingUserDetail'
import { AdminArchive } from './admin/AdminArchive'
import { AdminUsers } from './admin/AdminUsers'
import type { EventCategory } from '../types'

const categoryTabs: Record<string, EventCategory> = {
  treningi: 'TRAINING',
  obozy: 'CAMP',
  szkolenia: 'COURSE',
}

const tabs = [
  { key: 'kadra', ns: 'admin.tabs.kadra' },
  { key: 'treningi', ns: 'admin.tabs.trainings' },
  { key: 'obozy', ns: 'admin.tabs.camps' },
  { key: 'szkolenia', ns: 'admin.tabs.courses' },
  { key: 'uzytkownicy', ns: 'admin.tabs.users' },
  { key: 'archiwum', ns: 'admin.tabs.archive' },
] as const

const validTabs: Set<string> = new Set(tabs.map(item => item.key))

export function AdminPage() {
  const { t } = useTranslation('common')
  const { tab } = useParams<{ tab: string }>()
  const navigate = useNavigate()
  // When a person's profile is opened from the "Uczestnicy" section, it takes over the whole Treningi tab
  // (the global training sections below it are hidden) so it never looks like they belong to that one person.
  const [trainingUserId, setTrainingUserId] = useState<string | null>(null)

  if (!tab || !validTabs.has(tab)) {
    return <Navigate to="/admin/treningi" replace />
  }

  return (
    <div className="max-w-7xl mx-auto px-4 py-6">
      <h1 className="text-2xl font-bold text-surface-100 mb-6">{t('admin.title')}</h1>

      <div className="flex flex-wrap gap-1 mb-8 border-b border-surface-800 pb-1">
        {tabs.map(item => (
          <button
            key={item.key}
            onClick={() => { setTrainingUserId(null); navigate(`/admin/${item.key}`) }}
            className={clsx(
              'px-4 py-2 text-sm font-medium rounded-t-lg transition-colors',
              tab === item.key
                ? 'bg-surface-800 text-primary-400 border-b-2 border-primary-500'
                : 'text-surface-400 hover:text-surface-200 hover:bg-surface-800/50'
            )}
          >
            {t(item.ns)}
          </button>
        ))}
      </div>

      {tab === 'kadra' && <AdminInstructors />}

      {categoryTabs[tab] && (
        categoryTabs[tab] === 'TRAINING' && trainingUserId
          ? <AdminTrainingUserDetail userId={trainingUserId} onBack={() => setTrainingUserId(null)} />
          : <div className="space-y-12">
              {categoryTabs[tab] === 'TRAINING'
                ? <>
                    <AdminTrainingParticipants onOpenUser={setTrainingUserId} />
                    <AdminTrainingSlots />
                    <AdminCancelledSessions />
                    <AdminTrainingHolidays />
                    <AdminTrainingRefunds />
                  </>
                : <AdminEvents category={categoryTabs[tab]} />}
              <AdminEventTypes category={categoryTabs[tab]} />
            </div>
      )}

      {tab === 'uzytkownicy' && <AdminUsers />}
      {tab === 'archiwum' && <AdminArchive />}
    </div>
  )
}
