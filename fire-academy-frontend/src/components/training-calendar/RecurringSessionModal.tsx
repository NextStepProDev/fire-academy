import { useTranslation } from 'react-i18next'
import { Users } from 'lucide-react'
import { Modal } from '../ui/Modal'
import { AdminPrivateNote } from '../notes/AdminPrivateNote'
import { formatLongDate } from '../../utils/calendarRange'
import type { RecurringSession } from '../../types'

/**
 * A group session opened from the coach's copy of a client's calendar.
 *
 * The session is not a row anywhere — it is recomputed from the subscription on every read — so the
 * note hangs off (client, slot, date) instead of an id. That is also why this modal offers nothing
 * else: there is still nothing about the session itself to edit, exactly as the tile has always said.
 */
export function RecurringSessionModal({
  session, athleteId, onClose,
}: {
  session: RecurringSession
  athleteId: string
  onClose: () => void
}) {
  const { t } = useTranslation('calendar')

  return (
    <Modal isOpen onClose={onClose} title={t('notes.sessionTitle')}>
      <div className="space-y-1">
        <p className="flex items-center gap-1.5 font-medium text-surface-100">
          <Users className="h-4 w-4 shrink-0 text-surface-500" />
          {session.name}
        </p>
        <p className="text-sm text-surface-400">
          {formatLongDate(session.date)} · {session.startTime.slice(0, 5)}
          {session.endTime ? `–${session.endTime.slice(0, 5)}` : ''}
          {session.instructorName ? ` · ${session.instructorName}` : ''}
        </p>
      </div>

      <AdminPrivateNote
        anchor={{ target: 'session', id: session.slotId, athleteId, date: session.date }}
      />
    </Modal>
  )
}
