import { Plus } from 'lucide-react'
import { Modal } from '../ui/Modal'
import { Button } from '../ui/Button'
import { TrainingTile } from './TrainingTile'
import { RecurringTile } from './RecurringTile'
import { formatLongDate } from '../../utils/calendarRange'
import type { PersonalTraining, RecurringSession } from '../../types'

interface DaySheetProps {
  date: string
  trainings: PersonalTraining[]
  recurring: RecurringSession[]
  pasteArmed: boolean
  onClose: () => void
  onOpen: (training: PersonalTraining) => void
  onAdd: (date: string) => void
  onPaste: (date: string) => void
  onCopy?: (training: PersonalTraining) => void
  onCut?: (training: PersonalTraining) => void
  labels: {
    add: string
    copy: string
    cut: string
    pasteHere: string
    unread: string
    comments: string
    recurring: string
    task: string
    calories: string
    empty: string
  }
}

/**
 * One day, opened from the dot grid.
 * <p>
 * Everything a day cell cannot hold at phone size lives here: full titles, times, RPE, comment
 * counts, and the actions. The tiles are the same components the week view uses at full size —
 * a second rendering of a training is a second thing to keep in step.
 */
export function DaySheet({
  date, trainings, recurring, pasteArmed, onClose, onOpen, onAdd, onPaste, onCopy, onCut, labels,
}: DaySheetProps) {
  return (
    <Modal isOpen onClose={onClose} title={formatLongDate(date)}>
      <div className="space-y-3">
        {trainings.length === 0 && recurring.length === 0 && (
          <p className="text-sm text-surface-500">{labels.empty}</p>
        )}

        {trainings.map(training => (
          <TrainingTile
            key={training.id}
            training={training}
            onOpen={t => { onClose(); onOpen(t) }}
            onCopy={onCopy && (t => { onClose(); onCopy(t) })}
            onCut={onCut && (t => { onClose(); onCut(t) })}
            copyLabel={labels.copy}
            cutLabel={labels.cut}
            unreadLabel={labels.unread}
            commentsLabel={labels.comments}
            taskLabel={labels.task}
            caloriesLabel={labels.calories}
          />
        ))}

        {recurring.map(session => (
          <RecurringTile key={`${session.slotId}-${session.date}`} session={session} label={labels.recurring} />
        ))}

        {pasteArmed ? (
          <Button variant="primary" size="sm" className="w-full"
            onClick={() => { onClose(); onPaste(date) }}>
            {labels.pasteHere}
          </Button>
        ) : (
          <Button variant="ghost" size="sm" className="w-full"
            onClick={() => { onClose(); onAdd(date) }}>
            <Plus className="mr-1.5 h-4 w-4" />
            {labels.add}
          </Button>
        )}
      </div>
    </Modal>
  )
}
