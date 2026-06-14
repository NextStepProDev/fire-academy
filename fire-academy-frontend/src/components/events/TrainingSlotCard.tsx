import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Clock, User as UserIcon, Users } from 'lucide-react'
import { Button } from '../ui/Button'
import type { TrainingSlotCard as TrainingSlotCardType } from '../../types'

interface TrainingSlotCardProps {
  slot: TrainingSlotCardType
  isAuthenticated: boolean
  onEnroll: () => void
}

export function TrainingSlotCard({ slot, isAuthenticated, onEnroll }: TrainingSlotCardProps) {
  const { t } = useTranslation('events')
  const isFull = slot.availableSpots <= 0
  const time = `${slot.startTime.slice(0, 5)}${slot.endTime ? `–${slot.endTime.slice(0, 5)}` : ''}`

  return (
    <div className="bg-surface-900 border border-surface-800 rounded-xl p-4 flex flex-col sm:flex-row sm:items-center gap-3">
      <div className="flex-1 min-w-0 space-y-1">
        <h4 className="font-semibold text-surface-100">{slot.eventTypeName}</h4>
        <div className="flex flex-wrap gap-x-4 gap-y-1 text-sm text-surface-400">
          <span className="flex items-center gap-1.5"><Clock className="w-4 h-4" />{time}</span>
          {slot.instructorName && (
            <span className="flex items-center gap-1.5"><UserIcon className="w-4 h-4" />{slot.instructorName}</span>
          )}
          <span className="flex items-center gap-1.5">
            <Users className="w-4 h-4" />
            {t('slots.spotsOf', { available: slot.availableSpots, max: slot.maxParticipants })}
          </span>
        </div>
        {slot.price != null && (
          <p className="text-primary-400 font-semibold text-sm">{t('slots.pricePerSession', { price: slot.price })}</p>
        )}
      </div>
      <div className="sm:ml-auto">
        {isFull ? (
          <Button variant="ghost" size="sm" disabled>{t('slots.full')}</Button>
        ) : isAuthenticated ? (
          <Button variant="primary" size="sm" onClick={onEnroll}>{t('slots.enroll')}</Button>
        ) : (
          <Link to="/logowanie">
            <Button variant="secondary" size="sm">{t('slots.loginToEnroll')}</Button>
          </Link>
        )}
      </div>
    </div>
  )
}
