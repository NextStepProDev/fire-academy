import { useState } from 'react'
import { useParams, Link, Navigate } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { ArrowLeft, Calendar, MapPin, Users, Phone } from 'lucide-react'
import { publicApi } from '../api/public'
import { slugToCategory } from '../utils/categorySlug'
import { formatDateRange, formatSchedule } from '../utils/dates'
import { Seo } from '../components/seo/Seo'
import { ShareButton } from '../components/ui/ShareButton'
import { Button } from '../components/ui/Button'
import { EnrollmentModal } from '../components/events/EnrollmentModal'
import { LoadingSpinner } from '../components/ui/LoadingSpinner'
import { useEnrollGuard } from '../hooks/useEnrollGuard'

function isWithin24h(startDate: string, startTime: string | null): boolean {
  const dt = startTime
    ? new Date(`${startDate}T${startTime}`)
    : new Date(`${startDate}T00:00:00`)
  return (dt.getTime() - Date.now()) / (1000 * 60 * 60) < 24
}

export function EventDetailPage() {
  const { categorySlug, id } = useParams<{ categorySlug: string; id: string }>()
  const { t } = useTranslation('events')
  const category = categorySlug ? slugToCategory(categorySlug) : undefined

  const [enrollOpen, setEnrollOpen] = useState(false)
  const guardEnroll = useEnrollGuard()
  const queryClient = useQueryClient()

  const eventQuery = useQuery({
    queryKey: ['public', 'event', id],
    queryFn: () => publicApi.getEvent(id!),
    enabled: !!id && !!category,
  })

  const eventTypeQuery = useQuery({
    queryKey: ['public', 'event-type', eventQuery.data?.eventTypeId],
    queryFn: () => publicApi.getEventType(eventQuery.data!.eventTypeId!),
    enabled: !!eventQuery.data?.eventTypeId,
  })

  if (!category || !categorySlug) return <Navigate to="/" replace />

  if (eventQuery.isLoading) {
    return (
      <div className="flex justify-center py-20">
        <LoadingSpinner size="lg" />
      </div>
    )
  }

  if (eventQuery.error || !eventQuery.data) {
    return <Navigate to={`/${categorySlug}`} replace />
  }

  const event = eventQuery.data
  const thumbnail = eventTypeQuery.data?.thumbnailUrl ?? null
  const isFull = event.maxParticipants != null && event.availableSpots <= 0
  const tooLate = isWithin24h(event.startDate, event.startTime)
  const canEnroll = !isFull && !tooLate
  const shareUrl = `/${categorySlug}/termin/${id}`

  const dateStr = formatDateRange(event.startDate, event.endDate)
  const scheduleStr = formatSchedule(event.startDate, event.endDate, event.startTime, event.endTime)

  const seoDescription = [
    scheduleStr,
    event.location,
    event.price != null ? `${event.price} PLN` : null,
    event.description,
  ].filter(Boolean).join(' | ')

  const eventJsonLd = {
    '@context': 'https://schema.org',
    '@type': 'Event',
    name: event.eventTypeName,
    startDate: event.startTime ? `${event.startDate}T${event.startTime}` : event.startDate,
    ...(event.endDate && { endDate: event.endTime ? `${event.endDate}T${event.endTime}` : event.endDate }),
    ...(event.location && {
      location: { '@type': 'Place', name: event.location },
    }),
    ...(event.description && { description: event.description }),
    ...(thumbnail && { image: `${window.location.origin}${thumbnail}` }),
    ...(event.price != null && {
      offers: {
        '@type': 'Offer',
        price: String(event.price),
        priceCurrency: 'PLN',
        availability: isFull ? 'https://schema.org/SoldOut' : 'https://schema.org/InStock',
        url: `${window.location.origin}${shareUrl}`,
      },
    }),
    ...(event.maxParticipants != null && {
      maximumAttendeeCapacity: event.maxParticipants,
      remainingAttendeeCapacity: event.availableSpots,
    }),
    organizer: {
      '@type': 'Organization',
      name: 'Fire Academy',
      url: window.location.origin,
    },
    eventAttendanceMode: 'https://schema.org/OfflineEventAttendanceMode',
    eventStatus: 'https://schema.org/EventScheduled',
  }

  return (
    <>
      <Seo
        title={`${event.eventTypeName} — ${dateStr}`}
        description={seoDescription}
        path={shareUrl}
        image={thumbnail}
        jsonLd={eventJsonLd}
        breadcrumbs={[
          { name: 'Fire Academy', path: '/' },
          { name: t(`${({ TRAINING: 'trainings', CAMP: 'camps', COURSE: 'courses' } as const)[category!]}.title`), path: `/${categorySlug}` },
          { name: event.eventTypeName, path: shareUrl },
        ]}
      />

      <div className="max-w-4xl mx-auto px-4 py-10 space-y-8">
        <Link
          to={`/${categorySlug}`}
          className="inline-flex items-center gap-1.5 text-sm text-surface-400 hover:text-primary-400 transition-colors"
        >
          <ArrowLeft className="w-4 h-4" />
          {t('detail.backToList')}
        </Link>

        {thumbnail && (
          <div className="relative overflow-hidden rounded-xl">
            <img src={thumbnail} alt={event.eventTypeName} decoding="async" className="w-full aspect-video object-cover" />
            <div className="absolute inset-x-0 bottom-0 h-1/3 bg-gradient-to-t from-surface-950 to-transparent" />
          </div>
        )}

        <div className="flex items-start justify-between gap-4">
          <h1 className="text-3xl md:text-4xl font-bold text-surface-100">{event.eventTypeName}</h1>
          <ShareButton url={shareUrl} title={event.eventTypeName} className="shrink-0 mt-1" />
        </div>

        <div className="flex flex-wrap gap-x-6 gap-y-3 text-surface-300">
          <span className="flex items-center gap-2">
            <Calendar className="w-5 h-5 text-primary-500" />
            {scheduleStr}
          </span>
          {event.location && (
            <span className="flex items-center gap-2">
              <MapPin className="w-5 h-5 text-primary-500" />
              {event.location}
            </span>
          )}
          {event.maxParticipants != null && (
            <span className="flex items-center gap-2">
              <Users className="w-5 h-5 text-primary-500" />
              {t('event.spotsOf', { available: event.availableSpots, max: event.maxParticipants })}
            </span>
          )}
        </div>

        {event.price != null && (
          <p className="text-2xl font-bold text-primary-400">{event.price} PLN</p>
        )}

        {event.description && (
          <p className="text-surface-300 whitespace-pre-wrap leading-relaxed">{event.description}</p>
        )}

        <div className="pt-2">
          {isFull ? (
            <div className="space-y-2">
              <p className="text-surface-400">
                {t('event.spotsFull')}. {t('event.waitingList')}:
              </p>
              <a href="tel:+48534823667">
                <Button variant="waitlist">
                  <Phone className="w-4 h-4 mr-1.5" />
                  534 823 667
                </Button>
              </a>
            </div>
          ) : tooLate ? (
            <div className="space-y-2">
              <p className="text-surface-400">{t('event.tooLate')}</p>
              <a href="tel:+48534823667" className="text-primary-400 hover:text-primary-300 inline-flex items-center gap-1.5">
                <Phone className="w-4 h-4" />
                534 823 667
              </a>
            </div>
          ) : (
            <Button variant="primary" size="lg" onClick={() => guardEnroll(() => setEnrollOpen(true))}>
              {t('event.enroll')}
            </Button>
          )}
        </div>
      </div>

      <EnrollmentModal
        isOpen={enrollOpen}
        onClose={() => setEnrollOpen(false)}
        eventId={canEnroll ? event.id : null}
        eventName={event.eventTypeName}
        onEnrolled={() => queryClient.invalidateQueries({ queryKey: ['public'] })}
      />
    </>
  )
}
