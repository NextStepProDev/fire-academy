import { useState, useEffect, useRef } from 'react'
import { useParams, Navigate } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { ArrowLeft, Calendar, MapPin, Users, Phone, ChevronLeft, ChevronRight, X } from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import { publicApi } from '../api/public'
import { slugToCategory } from '../utils/categorySlug'
import { formatDateRange } from '../utils/dates'
import { visibleMonths, formatMonth, holidaysForDay } from '../utils/trainingSchedule'
import clsx from 'clsx'
import { Seo } from '../components/seo/Seo'
import { ShareButton } from '../components/ui/ShareButton'
import { Button } from '../components/ui/Button'
import { EnrollmentModal } from '../components/events/EnrollmentModal'
import { TrainingSlotCard } from '../components/events/TrainingSlotCard'
import { TrainingEnrollModal } from '../components/events/TrainingEnrollModal'
import { LoadingSpinner } from '../components/ui/LoadingSpinner'
import { useEnrollGuard } from '../hooks/useEnrollGuard'
import { useEnrolledSlot } from '../hooks/useEnrolledSlot'
import { useSmartBack } from '../hooks/useSmartBack'
import type { TrainingSlotCard as TrainingSlot } from '../types'

const DAYS = [1, 2, 3, 4, 5, 6, 7] as const

export function EventTypeDetailPage() {
  const { categorySlug, id } = useParams<{ categorySlug: string; id: string }>()
  const { t } = useTranslation('events')
  const { isAuthenticated } = useAuth()
  const isEnrolled = useEnrolledSlot()
  const goBack = useSmartBack(`/${categorySlug}`)
  const category = categorySlug ? slugToCategory(categorySlug) : undefined
  const isTraining = category === 'TRAINING'

  const months = visibleMonths()
  const [lightboxIndex, setLightboxIndex] = useState<number | null>(null)
  const [enrollEventId, setEnrollEventId] = useState<string | null>(null)
  const [enrollEventName, setEnrollEventName] = useState('')
  const [selectedMonth, setSelectedMonth] = useState(months[0])
  const [enrollSlot, setEnrollSlot] = useState<TrainingSlot | null>(null)
  const touchStart = useRef<number | null>(null)
  const guardEnroll = useEnrollGuard()
  const queryClient = useQueryClient()

  const eventTypeQuery = useQuery({
    queryKey: ['public', 'event-type', id],
    queryFn: () => publicApi.getEventType(id!),
    enabled: !!id && !!category,
    // Opt out of the global keepPreviousData: navigating to a different type should show a
    // spinner, not briefly flash the previous type's photo/description.
    placeholderData: undefined,
  })

  // Camps/courses list dated event instances; trainings list recurring weekly slots.
  const eventsQuery = useQuery({
    queryKey: ['public', 'events', category],
    queryFn: () => publicApi.getUpcomingEvents(category!),
    enabled: !!category && !isTraining,
  })

  const slotsQuery = useQuery({
    queryKey: ['public', 'training-slots', selectedMonth],
    queryFn: () => publicApi.getTrainingSlots(selectedMonth),
    enabled: isTraining,
  })
  const holidaysQuery = useQuery({
    queryKey: ['public', 'training-holidays', selectedMonth],
    queryFn: () => publicApi.getTrainingHolidays(selectedMonth),
    enabled: isTraining,
  })
  const holidays = holidaysQuery.data ?? []

  const photos = eventTypeQuery.data?.photos ?? []
  const relatedEvents = eventsQuery.data?.filter(e => e.eventTypeId === id) ?? []
  const relatedSlots = slotsQuery.data?.filter(s => s.eventTypeId === id) ?? []

  // Functional updates keep these stable-friendly; the React Compiler memoizes them.
  const prev = () => setLightboxIndex(i => (i === null ? i : (i - 1 + photos.length) % photos.length))
  const next = () => setLightboxIndex(i => (i === null ? i : (i + 1) % photos.length))

  useEffect(() => {
    if (lightboxIndex === null) return
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'ArrowLeft') setLightboxIndex(i => (i === null ? i : (i - 1 + photos.length) % photos.length))
      else if (e.key === 'ArrowRight') setLightboxIndex(i => (i === null ? i : (i + 1) % photos.length))
      else if (e.key === 'Escape') setLightboxIndex(null)
    }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [lightboxIndex, photos.length])

  if (!category || !categorySlug) return <Navigate to="/" replace />

  if (eventTypeQuery.isLoading) {
    return (
      <div className="flex justify-center py-20">
        <LoadingSpinner size="lg" />
      </div>
    )
  }

  if (eventTypeQuery.error || !eventTypeQuery.data) {
    return <Navigate to={`/${categorySlug}`} replace />
  }

  const et = eventTypeQuery.data
  const shareUrl = `/${categorySlug}/rodzaj/${id}`

  return (
    <>
      <Seo
        title={et.name}
        description={et.description || `${et.name} — sprawdź szczegóły i nadchodzące terminy w Fire Academy.`}
        path={shareUrl}
        image={et.thumbnailUrl}
        jsonLd={{
          '@context': 'https://schema.org',
          '@type': 'Course',
          name: et.name,
          ...(et.description && { description: et.description }),
          provider: {
            '@type': 'Organization',
            name: 'Fire Academy',
            url: window.location.origin,
          },
          ...(et.thumbnailUrl && { image: `${window.location.origin}${et.thumbnailUrl}` }),
          url: `${window.location.origin}${shareUrl}`,
          inLanguage: 'pl',
          ...(relatedEvents.length > 0 && {
            hasCourseInstance: relatedEvents.map(event => ({
              '@type': 'CourseInstance',
              courseMode: 'Offline',
              startDate: event.startTime ? `${event.startDate}T${event.startTime}` : event.startDate,
              ...(event.endDate && { endDate: event.endTime ? `${event.endDate}T${event.endTime}` : event.endDate }),
              ...(event.location && { location: { '@type': 'Place', name: event.location } }),
              ...(event.price != null && {
                offers: {
                  '@type': 'Offer',
                  price: String(event.price),
                  priceCurrency: 'PLN',
                  availability: event.maxParticipants != null && event.availableSpots <= 0
                    ? 'https://schema.org/SoldOut'
                    : 'https://schema.org/InStock',
                },
              }),
            })),
          }),
        }}
        breadcrumbs={[
          { name: 'Fire Academy', path: '/' },
          { name: t(`${({ TRAINING: 'trainings', CAMP: 'camps', COURSE: 'courses' } as const)[category!]}.title`), path: `/${categorySlug}` },
          { name: et.name, path: shareUrl },
        ]}
      />

      <div className="max-w-4xl mx-auto px-4 py-10 space-y-8">
        <button
          onClick={goBack}
          className="inline-flex items-center gap-1.5 text-sm text-surface-400 hover:text-primary-400 transition-colors"
        >
          <ArrowLeft className="w-4 h-4" />
          {t('detail.back')}
        </button>

        {et.thumbnailUrl && (
          <div className="aspect-square overflow-hidden rounded-xl bg-surface-800 max-w-md mx-auto">
            <img src={et.thumbnailUrl} alt={et.name} decoding="async" className="w-full h-full object-cover" />
          </div>
        )}

        <div className="flex items-start justify-between gap-4">
          <h1 className="text-3xl md:text-4xl font-bold text-surface-100">{et.name}</h1>
          <ShareButton url={shareUrl} title={et.name} className="shrink-0 mt-1" />
        </div>

        {et.description && (
          <p className="text-surface-300 whitespace-pre-wrap leading-relaxed">{et.description}</p>
        )}

        {photos.length > 0 && (
          <section>
            <h2 className="text-xl font-semibold text-surface-100 mb-4 border-l-4 border-primary-500 pl-3">
              {t('detail.gallery')}
            </h2>
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
              {photos.map((p, i) => (
                <button
                  key={p.id}
                  onClick={() => setLightboxIndex(i)}
                  className="aspect-square rounded-lg overflow-hidden bg-surface-800"
                >
                  <img src={p.url} alt="" loading="lazy" decoding="async" className="w-full h-full object-cover hover:scale-105 transition-transform" />
                </button>
              ))}
            </div>
          </section>
        )}

        {isTraining && (
          <section>
            <h2 className="text-xl font-semibold text-surface-100 mb-4 border-l-4 border-primary-500 pl-3">
              {t('detail.relatedEvents')}
            </h2>

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
            ) : relatedSlots.length ? (
              <div className={clsx('space-y-6 transition-opacity', slotsQuery.isFetching && 'opacity-60')}>
                {DAYS.map(day => {
                  const daySlots = relatedSlots.filter(s => s.dayOfWeek === day)
                  if (!daySlots.length) return null
                  return (
                    <div key={day} className="space-y-2">
                      <h3 className="font-semibold text-surface-300">{t(`days.${day}`)}</h3>
                      {daySlots.map(slot => (
                        <TrainingSlotCard
                          key={slot.id}
                          slot={slot}
                          holidayDates={holidaysForDay(holidays, slot.dayOfWeek)}
                          isAuthenticated={isAuthenticated}
                          alreadyEnrolled={isEnrolled(slot.id, selectedMonth)}
                          onEnroll={() => setEnrollSlot(slot)}
                        />
                      ))}
                    </div>
                  )
                })}
              </div>
            ) : (
              <p className="text-surface-500">{t('slots.noSlots')}</p>
            )}
          </section>
        )}

        {!isTraining && relatedEvents.length > 0 && (
          <section>
            <h2 className="text-xl font-semibold text-surface-100 mb-4 border-l-4 border-primary-500 pl-3">
              {t('detail.relatedEvents')}
            </h2>
            <div className="space-y-3">
              {relatedEvents.map(event => {
                const isFull = event.maxParticipants != null && event.availableSpots <= 0
                return (
                  <div key={event.id} className="flex flex-col sm:flex-row sm:items-center gap-3 bg-surface-900 border border-surface-800 rounded-lg p-4">
                    <div className="flex-1 min-w-0 space-y-1">
                      <div className="flex flex-wrap gap-x-4 gap-y-1 text-sm text-surface-400">
                        <span className="flex items-center gap-1.5">
                          <Calendar className="w-3.5 h-3.5" />
                          {formatDateRange(event.startDate, event.endDate)}
                          {event.startTime ? `, ${event.startTime}${event.endTime ? ` – ${event.endTime}` : ''}` : ''}
                        </span>
                        {event.location && (
                          <span className="flex items-center gap-1.5">
                            <MapPin className="w-3.5 h-3.5" />
                            {event.location}
                          </span>
                        )}
                        {event.maxParticipants != null && (
                          <span className="flex items-center gap-1.5">
                            <Users className="w-3.5 h-3.5" />
                            {t('event.spotsOf', { available: event.availableSpots, max: event.maxParticipants })}
                          </span>
                        )}
                      </div>
                      {event.price != null && (
                        <p className="text-primary-400 font-medium text-sm">{event.price} PLN</p>
                      )}
                    </div>
                    {isFull ? (
                      <a href="tel:+48534823667">
                        <Button variant="waitlist" size="sm">
                          <Phone className="w-4 h-4 mr-1.5" />
                          {t('event.waitingList')}
                        </Button>
                      </a>
                    ) : (
                      <Button
                        variant="primary"
                        size="sm"
                        onClick={() => guardEnroll(() => {
                          setEnrollEventId(event.id)
                          setEnrollEventName(event.eventTypeName)
                        })}
                      >
                        {t('event.enroll')}
                      </Button>
                    )}
                  </div>
                )
              })}
            </div>
          </section>
        )}
      </div>

      {lightboxIndex !== null && photos[lightboxIndex] && (
        <div
          className="fixed inset-0 z-[200] flex items-center justify-center bg-black/90"
          onClick={() => setLightboxIndex(null)}
          onTouchStart={e => { touchStart.current = e.touches[0].clientX }}
          onTouchEnd={e => {
            if (touchStart.current === null) return
            const diff = e.changedTouches[0].clientX - touchStart.current
            if (Math.abs(diff) > 50) { if (diff > 0) prev(); else next() }
            touchStart.current = null
          }}
        >
          <button
            onClick={e => { e.stopPropagation(); setLightboxIndex(null) }}
            className="absolute top-4 right-4 p-2 text-white/70 hover:text-white z-10"
          >
            <X className="w-6 h-6" />
          </button>

          {photos.length > 1 && (
            <>
              <button
                onClick={e => { e.stopPropagation(); prev() }}
                className="absolute left-3 top-1/2 -translate-y-1/2 p-2 rounded-full bg-black/50 text-white/80 hover:text-white hover:bg-black/70 transition-colors z-10"
              >
                <ChevronLeft className="w-6 h-6" />
              </button>
              <button
                onClick={e => { e.stopPropagation(); next() }}
                className="absolute right-3 top-1/2 -translate-y-1/2 p-2 rounded-full bg-black/50 text-white/80 hover:text-white hover:bg-black/70 transition-colors z-10"
              >
                <ChevronRight className="w-6 h-6" />
              </button>
            </>
          )}

          <img
            src={photos[lightboxIndex].url}
            alt=""
            decoding="async"
            className="max-w-[90vw] max-h-[90vh] object-contain rounded-lg"
            onClick={e => e.stopPropagation()}
          />

          {photos.length > 1 && (
            <div className="absolute bottom-4 left-1/2 -translate-x-1/2 flex gap-2">
              {photos.map((_, i) => (
                <button
                  key={i}
                  onClick={e => { e.stopPropagation(); setLightboxIndex(i) }}
                  className={`w-2 h-2 rounded-full transition-colors ${i === lightboxIndex ? 'bg-white' : 'bg-white/40'}`}
                />
              ))}
            </div>
          )}
        </div>
      )}

      <EnrollmentModal
        isOpen={!!enrollEventId}
        onClose={() => setEnrollEventId(null)}
        eventId={enrollEventId}
        eventName={enrollEventName}
        onEnrolled={() => queryClient.invalidateQueries({ queryKey: ['public'] })}
      />

      <TrainingEnrollModal
        slot={enrollSlot}
        startMonth={selectedMonth}
        holidays={holidays}
        onClose={() => setEnrollSlot(null)}
      />
    </>
  )
}
