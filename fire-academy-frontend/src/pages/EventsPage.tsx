import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { publicApi } from '../api/public'
import { Seo } from '../components/seo/Seo'
import { InstructorCard } from '../components/events/InstructorCard'
import { InstructorModal } from '../components/events/InstructorModal'
import { EventTypeCard } from '../components/events/EventTypeCard'
import { EventTypeModal } from '../components/events/EventTypeModal'
import { EventRow } from '../components/events/EventRow'
import { EnrollmentModal } from '../components/events/EnrollmentModal'
import { Modal } from '../components/ui/Modal'
import { LoadingSpinner } from '../components/ui/LoadingSpinner'
import { categoryToSlug } from '../utils/categorySlug'
import type { EventCategory, Instructor, EventType, EventInstance } from '../types'

interface EventsPageProps {
  category: EventCategory
}

export function EventsPage({ category }: EventsPageProps) {
  const { t } = useTranslation('events')
  const [selectedInstructor, setSelectedInstructor] = useState<Instructor | null>(null)
  const [selectedEventType, setSelectedEventType] = useState<EventType | null>(null)
  const [descriptionEvent, setDescriptionEvent] = useState<EventInstance | null>(null)
  const [enrollEventId, setEnrollEventId] = useState<string | null>(null)
  const [enrollEventName, setEnrollEventName] = useState('')

  const eventsQuery = useQuery({
    queryKey: ['public', 'events', category],
    queryFn: () => publicApi.getUpcomingEvents(category),
  })

  const eventTypesQuery = useQuery({
    queryKey: ['public', 'event-types', category],
    queryFn: () => publicApi.getEventTypes(category),
  })

  const instructorsQuery = useQuery({
    queryKey: ['public', 'instructors', category],
    queryFn: () => publicApi.getInstructors(category),
  })

  const handleDetails = (event: EventInstance) => {
    const eventType = eventTypesQuery.data?.find(et => et.id === event.eventTypeId)
    if (eventType && (eventType.description || eventType.photos.length > 0 || eventType.thumbnailUrl)) {
      setSelectedEventType(eventType)
    } else if (event.description) {
      setDescriptionEvent(event)
    }
  }

  const hasDetails = (event: EventInstance): boolean => {
    const eventType = eventTypesQuery.data?.find(et => et.id === event.eventTypeId)
    if (eventType && (eventType.description || eventType.photos.length > 0 || eventType.thumbnailUrl)) return true
    return !!event.description
  }

  const titleKey = { CAMP: 'camps.title', COURSE: 'courses.title', TRAINING: 'trainings.title' }[category]
  const pageTitle = t(titleKey)
  const slug = categoryToSlug(category)
  const descriptionMap: Record<string, string> = {
    TRAINING: 'Treningi indywidualne i grupowe w Fire Academy. Sprawdź nadchodzące terminy, rodzaje treningów i kadrę instruktorów.',
    CAMP: 'Obozy sportowe Fire Academy. Sprawdź terminy obozów, programy i dostępne miejsca.',
    COURSE: 'Szkolenia i kursy Fire Academy. Podnieś swoje umiejętności z doświadczoną kadrą.',
  }

  const eventsJsonLd = (eventsQuery.data ?? []).map(event => ({
    '@context': 'https://schema.org' as const,
    '@type': 'Event' as const,
    name: event.eventTypeName,
    startDate: event.startTime ? `${event.startDate}T${event.startTime}` : event.startDate,
    ...(event.endDate && { endDate: event.endTime ? `${event.endDate}T${event.endTime}` : event.endDate }),
    ...(event.location && { location: { '@type': 'Place' as const, name: event.location } }),
    ...(event.description && { description: event.description }),
    ...(event.price != null && {
      offers: {
        '@type': 'Offer' as const,
        price: String(event.price),
        priceCurrency: 'PLN',
        availability: event.maxParticipants != null && event.availableSpots <= 0
          ? 'https://schema.org/SoldOut'
          : 'https://schema.org/InStock',
        url: `${window.location.origin}/${slug}/termin/${event.id}`,
      },
    }),
    organizer: { '@type': 'Organization' as const, name: 'Fire Academy' },
    eventAttendanceMode: 'https://schema.org/OfflineEventAttendanceMode',
    url: `${window.location.origin}/${slug}/termin/${event.id}`,
  }))

  return (
    <div className="max-w-6xl mx-auto px-4 py-10 space-y-16">
      <Seo
        title={pageTitle}
        description={descriptionMap[category]}
        path={`/${slug}`}
        jsonLd={eventsJsonLd.length > 0 ? eventsJsonLd : undefined}
        breadcrumbs={[
          { name: 'Fire Academy', path: '/' },
          { name: pageTitle, path: `/${slug}` },
        ]}
      />
      <h1 className="text-3xl md:text-4xl font-bold text-surface-100">{pageTitle}</h1>

      {/* Terminy */}
      <section>
        <h2 className="text-2xl font-bold text-surface-100 mb-6 border-l-4 border-primary-500 pl-4">{t('sections.terminy')}</h2>
        {eventsQuery.isLoading ? (
          <LoadingSpinner />
        ) : eventsQuery.data?.length ? (
          <div className="space-y-4">
            {eventsQuery.data.map(event => (
              <EventRow
                key={event.id}
                event={event}
                onEnroll={() => {
                  setEnrollEventId(event.id)
                  setEnrollEventName(event.eventTypeName)
                }}
                onDetails={hasDetails(event) ? () => handleDetails(event) : undefined}
                shareUrl={`/${slug}/termin/${event.id}`}
              />
            ))}
          </div>
        ) : (
          <p className="text-surface-500">{t('noEvents')}</p>
        )}
      </section>

      {/* Rodzaje */}
      <section>
        <h2 className="text-2xl font-bold text-surface-100 mb-6 border-l-4 border-primary-500 pl-4">{t(`sections.rodzaje_${category}`)}</h2>
        {eventTypesQuery.isLoading ? (
          <LoadingSpinner />
        ) : eventTypesQuery.data?.length ? (
          <div className="space-y-3">
            {eventTypesQuery.data.map(et => (
              <EventTypeCard key={et.id} eventType={et} onClick={() => setSelectedEventType(et)} shareUrl={`/${slug}/rodzaj/${et.id}`} />
            ))}
          </div>
        ) : (
          <p className="text-surface-500">{t('noEventTypes')}</p>
        )}
      </section>

      {/* O nas */}
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

      <InstructorModal instructor={selectedInstructor} onClose={() => setSelectedInstructor(null)} />
      <EventTypeModal
        eventType={selectedEventType}
        events={eventsQuery.data?.filter(e => e.eventTypeId === selectedEventType?.id) ?? []}
        onEnroll={(eventId, eventName) => {
          setSelectedEventType(null)
          setEnrollEventId(eventId)
          setEnrollEventName(eventName)
        }}
        onClose={() => setSelectedEventType(null)}
      />
      {descriptionEvent && (
        <Modal isOpen onClose={() => setDescriptionEvent(null)} title={descriptionEvent.eventTypeName}>
          <p className="text-surface-300 whitespace-pre-wrap">{descriptionEvent.description}</p>
        </Modal>
      )}
      <EnrollmentModal
        isOpen={!!enrollEventId}
        onClose={() => setEnrollEventId(null)}
        eventId={enrollEventId}
        eventName={enrollEventName}
      />
    </div>
  )
}
