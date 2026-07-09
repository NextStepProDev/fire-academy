import { useTranslation } from 'react-i18next'
import { Modal } from '../ui/Modal'
import { User, Clock } from 'lucide-react'
import type { Instructor, TrainingSlotCard } from '../../types'

interface InstructorModalProps {
  instructor: Instructor | null
  onClose: () => void
  schedule?: TrainingSlotCard[]
}

export function InstructorModal({ instructor, onClose, schedule }: InstructorModalProps) {
  const { t } = useTranslation('events')
  if (!instructor) return null
  const slots = schedule ?? []
  return (
    <Modal isOpen={!!instructor} onClose={onClose} size="lg" title={`${instructor.firstName} ${instructor.lastName}`}>
      <div className="flex flex-col gap-4">
        <div className="w-40 h-40 rounded-full overflow-hidden bg-surface-800 flex items-center justify-center mx-auto">
          {instructor.photoUrl ? (
            <img src={instructor.photoUrl} alt={instructor.firstName} decoding="async" className="w-full h-full object-cover" />
          ) : (
            <User className="w-20 h-20 text-surface-600" />
          )}
        </div>
        {instructor.bio && (
          <p className="text-surface-300 whitespace-pre-wrap">{instructor.bio}</p>
        )}
        {slots.length > 0 && (
          <div>
            <h3 className="text-sm font-semibold uppercase tracking-wide text-primary-400 mb-2">{t('slots.schedule')}</h3>
            <ul className="space-y-1.5">
              {slots.map(s => (
                <li key={s.id} className="flex items-center gap-2 text-sm text-surface-300">
                  <Clock className="w-4 h-4 text-surface-500 shrink-0" />
                  <span className="text-surface-100 font-medium">{t(`days.${s.dayOfWeek}`)}</span>
                  <span>{s.startTime.slice(0, 5)}{s.endTime ? `–${s.endTime.slice(0, 5)}` : ''}</span>
                  <span className="text-surface-500">· {s.eventTypeName}</span>
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>
    </Modal>
  )
}
