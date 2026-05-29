import { MapPin, Calendar, Users } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Button } from '../ui/Button'
import { formatDateRange } from '../../utils/dates'
import type { EventInstance } from '../../types'

interface EventRowProps {
  event: EventInstance
  onEnroll: () => void
  onDetails?: () => void
}

export function EventRow({ event, onEnroll, onDetails }: EventRowProps) {
  const { t } = useTranslation('events')
  const isFull = event.maxParticipants != null && event.availableSpots <= 0

  return (
    <div className="bg-surface-900 border border-surface-800 rounded-xl p-5 flex flex-col sm:flex-row sm:items-center gap-4">
      <div className="flex-1 space-y-2">
        <h3 className="font-semibold text-surface-100 text-lg">{event.eventTypeName}</h3>
        <div className="flex flex-wrap gap-x-5 gap-y-1 text-sm text-surface-400">
          <span className="flex items-center gap-1.5">
            <Calendar className="w-4 h-4" />
            {formatDateRange(event.startDate, event.endDate)}
            {event.startTime ? `, ${event.startTime}${event.endTime ? ` – ${event.endTime}` : ''}` : ''}
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
      </div>
      <div className="flex gap-2 sm:ml-auto">
        {onDetails && (
          <Button variant="ghost" size="sm" onClick={onDetails}>
            {t('event.details')}
          </Button>
        )}
        <Button
          variant="primary"
          size="sm"
          onClick={onEnroll}
          disabled={isFull}
        >
          {isFull ? t('event.spotsFull') : t('event.enroll')}
        </Button>
      </div>
    </div>
  )
}
