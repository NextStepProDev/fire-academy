import { useTranslation } from 'react-i18next'
import { useParams, useNavigate, Navigate } from 'react-router-dom'
import clsx from 'clsx'
import { AdminInstructors } from './admin/AdminInstructors'
import { AdminEventTypes } from './admin/AdminEventTypes'
import { AdminEvents } from './admin/AdminEvents'
import { AdminArchive } from './admin/AdminArchive'
import { AdminRodo } from './admin/AdminRodo'
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
  { key: 'rodo', ns: 'admin.tabs.rodo' },
] as const

const validTabs: Set<string> = new Set(tabs.map(item => item.key))

export function AdminPage() {
  const { t } = useTranslation('common')
  const { tab } = useParams<{ tab: string }>()
  const navigate = useNavigate()

  if (!tab || !validTabs.has(tab)) {
    return <Navigate to="/admin/kadra" replace />
  }

  return (
    <div className="max-w-7xl mx-auto px-4 py-6">
      <h1 className="text-2xl font-bold text-surface-100 mb-6">{t('admin.title')}</h1>

      <div className="flex flex-wrap gap-1 mb-8 border-b border-surface-800 pb-1">
        {tabs.map(item => (
          <button
            key={item.key}
            onClick={() => navigate(`/admin/${item.key}`)}
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
        <div className="space-y-12">
          <AdminEvents category={categoryTabs[tab]} />
          <AdminEventTypes category={categoryTabs[tab]} />
        </div>
      )}

      {tab === 'uzytkownicy' && <AdminUsers />}
      {tab === 'archiwum' && <AdminArchive />}
      {tab === 'rodo' && <AdminRodo />}
    </div>
  )
}
