import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import clsx from 'clsx'
import { AdminInstructors } from './admin/AdminInstructors'
import { AdminEventTypes } from './admin/AdminEventTypes'
import { AdminEvents } from './admin/AdminEvents'
import { AdminEnrollments } from './admin/AdminEnrollments'

const tabs = [
  { key: 'instructors', ns: 'admin.tabs.instructors' },
  { key: 'camp-types', ns: 'admin.tabs.campTypes' },
  { key: 'course-types', ns: 'admin.tabs.courseTypes' },
  { key: 'camp-events', ns: 'admin.tabs.campEvents' },
  { key: 'course-events', ns: 'admin.tabs.courseEvents' },
  { key: 'enrollments', ns: 'admin.tabs.enrollments' },
] as const

export function AdminPage() {
  const { t } = useTranslation('common')
  const [activeTab, setActiveTab] = useState('instructors')

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

      {activeTab === 'instructors' && <AdminInstructors />}
      {activeTab === 'camp-types' && <AdminEventTypes category="CAMP" />}
      {activeTab === 'course-types' && <AdminEventTypes category="COURSE" />}
      {activeTab === 'camp-events' && <AdminEvents category="CAMP" />}
      {activeTab === 'course-events' && <AdminEvents category="COURSE" />}
      {activeTab === 'enrollments' && <AdminEnrollments />}
    </div>
  )
}
