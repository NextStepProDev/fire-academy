import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { ChevronDown, ChevronRight } from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import { publicApi } from '../api/public'
import { Seo } from '../components/seo/Seo'
import { InstructorCard } from '../components/events/InstructorCard'
import { InstructorModal } from '../components/events/InstructorModal'
import { EventTypeCard } from '../components/events/EventTypeCard'
import { EventTypeModal } from '../components/events/EventTypeModal'
import { TrainingSlotCard } from '../components/events/TrainingSlotCard'
import { TrainingEnrollModal } from '../components/events/TrainingEnrollModal'
import { LoadingSpinner } from '../components/ui/LoadingSpinner'
import { visibleMonths, formatMonth } from '../utils/trainingSchedule'
import clsx from 'clsx'
import type { Instructor, EventType, TrainingSlotCard as TrainingSlot } from '../types'

const DAYS = [1, 2, 3, 4, 5, 6, 7] as const

export function TrainingsPage() {
  const { t } = useTranslation('events')
  const { isAuthenticated } = useAuth()

  const months = visibleMonths()
  const [selectedMonth, setSelectedMonth] = useState(months[0])
  const [enrollSlot, setEnrollSlot] = useState<TrainingSlot | null>(null)
  const [selectedEventType, setSelectedEventType] = useState<EventType | null>(null)
  const [selectedInstructor, setSelectedInstructor] = useState<Instructor | null>(null)
  const [expandedDays, setExpandedDays] = useState<Set<number>>(new Set())

  const toggleDay = (day: number) => setExpandedDays(prev => {
    const next = new Set(prev)
    if (next.has(day)) next.delete(day); else next.add(day)
    return next
  })

  const slotsQuery = useQuery({
    queryKey: ['public', 'training-slots', selectedMonth],
    queryFn: () => publicApi.getTrainingSlots(selectedMonth),
  })
  const eventTypesQuery = useQuery({
    queryKey: ['public', 'event-types', 'TRAINING'],
    queryFn: () => publicApi.getEventTypes('TRAINING'),
  })
  const instructorsQuery = useQuery({
    queryKey: ['public', 'instructors', 'TRAINING'],
    queryFn: () => publicApi.getInstructors('TRAINING'),
  })

  const slots = slotsQuery.data ?? []

  const openType = (eventTypeId: string) => {
    const et = eventTypesQuery.data?.find(e => e.id === eventTypeId)
    if (et) setSelectedEventType(et)
  }
  const findInstructor = (instructorId: string | null) =>
    instructorId ? instructorsQuery.data?.find(i => i.id === instructorId) ?? null : null

  return (
    <div className="max-w-6xl mx-auto px-4 py-10 space-y-16">
      <Seo
        title={t('trainings.title')}
        description="Treningi indywidualne i grupowe w Fire Academy. Sprawdź cykliczne terminy, rodzaje treningów i kadrę instruktorów."
        path="/treningi"
        breadcrumbs={[
          { name: 'Fire Academy', path: '/' },
          { name: t('trainings.title'), path: '/treningi' },
        ]}
      />

      <h1 className="text-3xl md:text-4xl font-bold text-surface-100">{t('trainings.title')}</h1>

      {/* Recurring sessions */}
      <section>
        <h2 className="text-2xl font-bold text-surface-100 mb-6 border-l-4 border-primary-500 pl-4">{t('sections.terminy')}</h2>

        {/* Month selector */}
        <div className="flex flex-wrap gap-2 mb-6">
          {months.map(m => (
            <button
              key={m}
              onClick={() => setSelectedMonth(m)}
              className={clsx(
                'px-4 py-2 text-sm font-medium rounded-lg capitalize transition-colors',
                m === selectedMonth ? 'bg-primary-600 text-white' : 'bg-surface-900 border border-surface-800 text-surface-300 hover:bg-surface-800'
              )}
            >
              {formatMonth(m)}
            </button>
          ))}
        </div>

        {slotsQuery.isLoading ? (
          <LoadingSpinner />
        ) : slots.length ? (
          <div className="space-y-3">
            {DAYS.map(day => {
              const daySlots = slots.filter(s => s.dayOfWeek === day)
              if (!daySlots.length) return null
              const expanded = expandedDays.has(day)
              return (
                <div key={day}>
                  <button
                    onClick={() => toggleDay(day)}
                    className="w-full flex items-center justify-between px-4 py-3 bg-surface-900 border border-surface-800 rounded-xl hover:bg-surface-800/50 transition-colors"
                  >
                    <span className="flex items-center gap-2 font-semibold text-surface-100">
                      {expanded ? <ChevronDown className="w-5 h-5 text-primary-400" /> : <ChevronRight className="w-5 h-5 text-surface-400" />}
                      {t(`days.${day}`)}
                      <span className="text-sm font-normal text-surface-500">({daySlots.length})</span>
                    </span>
                  </button>
                  {expanded && (
                    <div className="space-y-2 mt-2">
                      {daySlots.map(slot => {
                        const instr = findInstructor(slot.instructorId)
                        return (
                          <TrainingSlotCard
                            key={slot.id}
                            slot={slot}
                            isAuthenticated={isAuthenticated}
                            onEnroll={() => setEnrollSlot(slot)}
                            onOpenType={() => openType(slot.eventTypeId)}
                            onOpenInstructor={instr ? () => setSelectedInstructor(instr) : undefined}
                          />
                        )
                      })}
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        ) : (
          <p className="text-surface-500">{t('slots.noSlots')}</p>
        )}
      </section>

      {/* Types */}
      <section>
        <h2 className="text-2xl font-bold text-surface-100 mb-6 border-l-4 border-primary-500 pl-4">{t('sections.rodzaje_TRAINING')}</h2>
        {eventTypesQuery.isLoading ? (
          <LoadingSpinner />
        ) : eventTypesQuery.data?.length ? (
          <div className="space-y-3">
            {eventTypesQuery.data.map(et => (
              <EventTypeCard key={et.id} eventType={et} onClick={() => setSelectedEventType(et)} shareUrl={`/treningi/rodzaj/${et.id}`} />
            ))}
          </div>
        ) : (
          <p className="text-surface-500">{t('noEventTypes')}</p>
        )}
      </section>

      {/* Staff */}
      <section>
        <h2 className="text-2xl font-bold text-surface-100 mb-6 border-l-4 border-primary-500 pl-4">{t('sections.oNas')}</h2>
        {instructorsQuery.isLoading ? (
          <LoadingSpinner />
        ) : instructorsQuery.data?.length ? (
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-6">
            {instructorsQuery.data.map(instr => (
              <InstructorCard key={instr.id} instructor={instr} onClick={() => setSelectedInstructor(instr)} shareUrl={`/kadra/${instr.id}`} />
            ))}
          </div>
        ) : (
          <p className="text-surface-500">{t('noInstructors')}</p>
        )}
      </section>

      <InstructorModal
        instructor={selectedInstructor}
        onClose={() => setSelectedInstructor(null)}
        schedule={selectedInstructor ? slots.filter(s => s.instructorId === selectedInstructor.id) : []}
      />
      <EventTypeModal
        eventType={selectedEventType}
        events={[]}
        onEnroll={() => {}}
        onClose={() => setSelectedEventType(null)}
      />
      <TrainingEnrollModal
        slot={enrollSlot}
        startMonth={selectedMonth}
        onClose={() => setEnrollSlot(null)}
      />
    </div>
  )
}
