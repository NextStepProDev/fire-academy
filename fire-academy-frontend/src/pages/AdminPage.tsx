import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import clsx from 'clsx'
import { AdminInstructors } from './admin/AdminInstructors'
import { AdminEventTypes } from './admin/AdminEventTypes'
import { AdminEvents } from './admin/AdminEvents'
import type { EventCategory } from '../types'

const categoryTabs: Record<string, EventCategory> = {
  trainings: 'TRAINING',
  camps: 'CAMP',
  courses: 'COURSE',
}

const tabs = [
  { key: 'kadra', ns: 'admin.tabs.kadra' },
  { key: 'trainings', ns: 'admin.tabs.trainings' },
  { key: 'camps', ns: 'admin.tabs.camps' },
  { key: 'courses', ns: 'admin.tabs.courses' },
] as const

export function AdminPage() {
  const { t } = useTranslation('common')
  const [activeTab, setActiveTab] = useState('kadra')

  return (
    <div className="max-w-7xl mx-auto px-4 py-6">
      <h1 className="text-2xl font-bold text-surface-100 mb-6">{t('admin.title')}</h1>

      <div className="flex flex-wrap gap-1 mb-8 border-b border-surface-800 pb-1">
        {tabs.map(tab => (
          <button
            key={tab.key}
            onClick={() => setActiveTab(tab.key)}
            className={clsx(
              'px-4 py-2 text-sm font-medium rounded-t-lg transition-colors',
              activeTab === tab.key
                ? 'bg-surface-800 text-primary-400 border-b-2 border-primary-500'
                : 'text-surface-400 hover:text-surface-200 hover:bg-surface-800/50'
            )}
          >
            {t(tab.ns)}
          </button>
        ))}
      </div>

      {activeTab === 'kadra' && <AdminInstructors />}

      {categoryTabs[activeTab] && (
        <div className="space-y-12">
          <AdminEvents category={categoryTabs[activeTab]} />
          <AdminEventTypes category={categoryTabs[activeTab]} />
        </div>
      )}

    </div>
  )
}
