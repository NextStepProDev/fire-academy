import { useState, Fragment } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { adminApi } from '../../api/admin'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { ChevronDown, ChevronRight, MessageSquare } from 'lucide-react'
import type { EventCategory, EventInstance } from '../../types'
import clsx from 'clsx'

type ArchivedEvent = EventInstance & { category: EventCategory }

const categoryConfig: Record<EventCategory, { labelKey: string; style: string }> = {
  TRAINING: { labelKey: 'archive.categoryTraining', style: 'bg-primary-500/20 text-primary-400' },
  CAMP: { labelKey: 'archive.categoryCamp', style: 'bg-blue-500/20 text-blue-400' },
  COURSE: { labelKey: 'archive.categoryCourse', style: 'bg-green-500/20 text-green-400' },
}

const filterOptions: ('ALL' | EventCategory)[] = ['ALL', 'TRAINING', 'CAMP', 'COURSE']

function isPastEvent(event: EventInstance): boolean {
  const today = new Date().toISOString().split('T')[0]
  return (event.endDate ?? event.startDate) < today
}

function ArchiveCard({ event }: { event: ArchivedEvent }) {
  const { t } = useTranslation('admin')
  const [expanded, setExpanded] = useState(false)
  const [expandedNote, setExpandedNote] = useState<string | null>(null)
  const config = categoryConfig[event.category]

  const { data: enrollments } = useQuery({
    queryKey: ['admin', 'enrollments', event.id],
    queryFn: () => adminApi.getEnrollmentsByEvent(event.id),
    enabled: expanded,
    staleTime: 0,
  })

  return (
    <div className="bg-surface-900 border border-surface-800 rounded-xl overflow-hidden opacity-75">
      <div
        className="px-4 py-4 flex items-start gap-4 cursor-pointer hover:bg-surface-800/30 transition-colors"
        onClick={() => setExpanded(!expanded)}
      >
        <div className="mt-0.5 p-0.5 text-surface-400 shrink-0">
          {expanded ? <ChevronDown className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />}
        </div>

        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-0.5">
            <span className={clsx('px-2 py-0.5 text-xs rounded-full font-medium', config.style)}>
              {t(config.labelKey)}
            </span>
            <p className="font-medium text-surface-100">{event.eventTypeName}</p>
          </div>
          <p className="text-sm text-surface-400">
            {event.startDate}{event.endDate ? ` – ${event.endDate}` : ''}
            {event.startTime ? ` · ${event.startTime}${event.endTime ? ` – ${event.endTime}` : ''}` : ''}
            {event.location ? ` · ${event.location}` : ''}
          </p>
          <p className="text-sm text-surface-500">
            {event.price != null && `${event.price} PLN · `}
            {t('events.enrolled')}: {event.enrollmentCount}
            {event.maxParticipants != null && ` / ${event.maxParticipants}`}
          </p>
        </div>
      </div>

      {expanded && (
        <div className="border-t border-surface-800 px-5 py-3">
          {!enrollments?.length ? (
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
                  {enrollments.map(en => (
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
                          {en.note && (
                            <button
                              onClick={(e) => { e.stopPropagation(); setExpandedNote(expandedNote === en.id ? null : en.id) }}
                              className={`p-1 ${expandedNote === en.id ? 'text-primary-400' : 'text-surface-400 hover:text-primary-400'}`}
                              title={t('enrollments.note')}
                            >
                              <MessageSquare className="w-4 h-4" />
                            </button>
                          )}
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
    </div>
  )
}

export function AdminArchive() {
  const { t } = useTranslation('admin')
  const [selectedCategory, setSelectedCategory] = useState<'ALL' | EventCategory>('ALL')

  const { data: trainings, isLoading: l1 } = useQuery({
    queryKey: ['admin', 'events', 'TRAINING'],
    queryFn: () => adminApi.getEvents('TRAINING'),
    staleTime: 0,
  })
  const { data: camps, isLoading: l2 } = useQuery({
    queryKey: ['admin', 'events', 'CAMP'],
    queryFn: () => adminApi.getEvents('CAMP'),
    staleTime: 0,
  })
  const { data: courses, isLoading: l3 } = useQuery({
    queryKey: ['admin', 'events', 'COURSE'],
    queryFn: () => adminApi.getEvents('COURSE'),
    staleTime: 0,
  })

  if (l1 || l2 || l3) return <LoadingSpinner />

  const tagCategory = (events: EventInstance[] | undefined, cat: EventCategory): ArchivedEvent[] =>
    (events ?? []).filter(isPastEvent).map(e => ({ ...e, category: cat }))

  const allPast: ArchivedEvent[] = [
    ...tagCategory(trainings, 'TRAINING'),
    ...tagCategory(camps, 'CAMP'),
    ...tagCategory(courses, 'COURSE'),
  ].sort((a, b) => b.startDate.localeCompare(a.startDate))

  const filtered = selectedCategory === 'ALL'
    ? allPast
    : allPast.filter(e => e.category === selectedCategory)

  const filterLabels: Record<string, string> = {
    ALL: t('archive.all'),
    TRAINING: t('archive.categoryTraining'),
    CAMP: t('archive.categoryCamp'),
    COURSE: t('archive.categoryCourse'),
  }

  return (
    <div>
      <h2 className="text-xl font-semibold text-surface-100 mb-6">{t('archive.title')}</h2>

      <div className="flex gap-2 mb-6">
        {filterOptions.map(cat => (
          <button
            key={cat}
            onClick={() => setSelectedCategory(cat)}
            className={clsx(
              'px-3 py-1.5 text-sm rounded-lg transition-colors',
              selectedCategory === cat
                ? 'bg-primary-500/20 text-primary-400'
                : 'bg-surface-800 text-surface-400 hover:text-surface-200'
            )}
          >
            {filterLabels[cat]}
          </button>
        ))}
      </div>

      {!filtered.length ? (
        <p className="text-surface-500">{t('archive.noItems')}</p>
      ) : (
        <div className="space-y-3">
          {filtered.map(ev => (
            <ArchiveCard key={ev.id} event={ev} />
          ))}
        </div>
      )}
    </div>
  )
}
