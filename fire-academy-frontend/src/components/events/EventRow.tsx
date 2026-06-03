import { MapPin, Calendar, Users, Phone } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Button } from '../ui/Button'
import { ShareButton } from '../ui/ShareButton'
import { formatSchedule } from '../../utils/dates'
import type { EventInstance } from '../../types'

interface EventRowProps {
  event: EventInstance
  onEnroll: () => void
  onDetails?: () => void
  shareUrl?: string
}

function isWithin24h(event: EventInstance): boolean {
  const startDateTime = event.startTime
    ? new Date(`${event.startDate}T${event.startTime}`)
    : new Date(`${event.startDate}T00:00:00`)
  const hoursLeft = (startDateTime.getTime() - Date.now()) / (1000 * 60 * 60)
  return hoursLeft < 24
}

export function EventRow({ event, onEnroll, onDetails, shareUrl }: EventRowProps) {
  const { t } = useTranslation('events')
  const isFull = event.maxParticipants != null && event.availableSpots <= 0
  const tooLate = isWithin24h(event)

  return (
    <div className="bg-surface-900 border border-surface-800 rounded-xl p-5 flex flex-col sm:flex-row sm:items-center gap-4">
      <div className="flex-1 space-y-2">
        <h3 className="font-semibold text-surface-100 text-lg">{event.eventTypeName}</h3>
        <div className="flex flex-wrap gap-x-5 gap-y-1 text-sm text-surface-400">
          <span className="flex items-center gap-1.5">
            <Calendar className="w-4 h-4" />
            {formatSchedule(event.startDate, event.endDate, event.startTime, event.endTime)}
          </span>
          {event.location && (
            <span className="flex items-center gap-1.5">
              <MapPin className="w-4 h-4" />
              {event.location}
            </span>
          )}
          {event.maxParticipants != null && (
            <span className="flex items-center gap-1.5">
              <Users className="w-4 h-4" />
              {t('event.spotsOf', { available: event.availableSpots, max: event.maxParticipants })}
            </span>
          )}
        </div>
        {event.price != null && (
          <p className="text-primary-400 font-semibold">{event.price} PLN</p>
        )}
        {isFull && (
          <p className="text-sm text-surface-400">
            {t('event.spotsFull')}. {t('event.waitingList')}:
            <a href="tel:+48534823667" className="ml-1 text-primary-400 hover:text-primary-300 inline-flex items-center gap-1">
              <Phone className="w-3.5 h-3.5" />534 823 667
            </a>
          </p>
        )}
        {tooLate && !isFull && (
          <p className="text-sm text-surface-400">
            {t('event.tooLate')}
            <a href="tel:+48534823667" className="ml-1 text-primary-400 hover:text-primary-300 inline-flex items-center gap-1">
              <Phone className="w-3.5 h-3.5" />534 823 667
            </a>
          </p>
        )}
      </div>
      <div className="flex items-center gap-2 sm:ml-auto">
        {shareUrl && (
          <ShareButton url={shareUrl} title={event.eventTypeName} />
        )}
        {onDetails && (
          <Button variant="ghost" size="sm" onClick={onDetails}>
            {t('event.details')}
          </Button>
        )}
        {isFull ? (
          <a href="tel:+48534823667">
            <Button variant="primary" size="sm">
              <Phone className="w-4 h-4 mr-1.5" />
              {t('event.waitingList')}
            </Button>
          </a>
        ) : (
          <Button
            variant="primary"
            size="sm"
            onClick={onEnroll}
            disabled={tooLate}
          >
            {tooLate ? t('event.enrollClosed') : t('event.enroll')}
          </Button>
        )}
      </div>
    </div>
  )
}
