import clsx from 'clsx'
import { Plus } from 'lucide-react'
import { dayOfMonth, isOutsideMonth, isWeekend, todayIso, weekdayShort } from '../../utils/calendarRange'
import type { PersonalTraining, RecurringSession } from '../../types'
import { TrainingTile } from './TrainingTile'
import { RecurringTile } from './RecurringTile'

interface DayColumnProps {
  date: string
  anchor: string
  trainings: PersonalTraining[]
  recurring: RecurringSession[]
  compact?: boolean
  showWeekday?: boolean
  cutId?: string | null
  pasteArmed?: boolean
  onOpen: (training: PersonalTraining) => void
  onAdd: (date: string) => void
  onPaste?: (date: string) => void
  onCopy?: (training: PersonalTraining) => void
  onCut?: (training: PersonalTraining) => void
  labels: {
    add: string; copy: string; cut: string; pasteHere: string
    unread: string; comments: string; recurring: string; task: string; calories: string
  }
}

/**
 * One day, in both the week and the month grid — the two views differ only in density, so they share
 * this component rather than diverging into two layouts that drift apart.
 *
 * Cards stack top-to-bottom in the order the API returns them (untimed first, then by hour). There is
 * no absolute positioning, no pixel maths and no overlap lanes: without an hour axis a day is just a
 * short list, and its height follows its content.
 */
export function DayColumn({
  date, anchor, trainings, recurring, compact = false, showWeekday = true, cutId, pasteArmed,
  onOpen, onAdd, onPaste, onCopy, onCut, labels,
}: DayColumnProps) {
  const isToday = date === todayIso()
  const outside = compact && isOutsideMonth(date, anchor)

  return (
    <div
      className={clsx(
        'group flex flex-col rounded-lg border border-surface-800 bg-surface-900/60 p-1.5',
        compact ? 'min-h-24 gap-1' : 'min-h-40 gap-2',
        isToday && 'ring-1 ring-primary-500/40',
        outside && 'opacity-40',
        pasteArmed && 'cursor-copy hover:border-primary-600/60',
      )}
      // While the clipboard is armed the whole day is the drop target. The cards inside go inert so
      // one tap cannot both paste and open a card.
      onClick={pasteArmed && onPaste ? () => onPaste(date) : undefined}
    >
      <div className="flex items-baseline justify-between px-0.5">
        <span className="text-xs text-surface-400">
          {showWeekday && <span className="mr-1">{weekdayShort(date)}</span>}
          <span className={clsx('font-semibold', isToday ? 'text-primary-400' : 'text-surface-200')}>
            {dayOfMonth(date)}
          </span>
        </span>
        {isWeekend(date) && !compact && <span className="text-[10px] uppercase text-surface-600">wolne</span>}
      </div>

      <div className={clsx('flex flex-col', compact ? 'gap-1' : 'gap-2')}>
        {trainings.map(training => (
          <TrainingTile
            key={training.id}
            training={training}
            compact={compact}
            cut={cutId === training.id}
            inert={pasteArmed}
            onOpen={onOpen}
            onCopy={onCopy}
            onCut={onCut}
            copyLabel={labels.copy}
            cutLabel={labels.cut}
            unreadLabel={labels.unread}
            commentsLabel={labels.comments}
            taskLabel={labels.task}
            caloriesLabel={labels.calories}
          />
        ))}
        {/* Group sessions come last: the 1-on-1 plan is what this calendar is for, and these are
            context around it. */}
        {recurring.map(session => (
          <RecurringTile
            key={`${session.slotId}-${session.date}`}
            session={session}
            compact={compact}
            label={labels.recurring}
          />
        ))}
      </div>

      {pasteArmed ? (
        <span className="mt-auto rounded border border-dashed border-primary-600/50 px-1 py-1 text-center text-[11px] text-primary-300">
          {labels.pasteHere}
        </span>
      ) : (
        <button
          type="button"
          aria-label={`${labels.add} ${date}`}
          onClick={() => onAdd(date)}
          className={clsx(
            'mt-auto flex items-center justify-center rounded border border-dashed border-surface-700',
            // One `transition` for colour and opacity alike: two transition-* utilities would fight
            // over the same property and the winner would depend on stylesheet order, not on the
            // order written here.
            'text-surface-500 transition hover:border-primary-600/50 hover:text-primary-400',
            // Faded out only where a pointer can bring it back. The base state has to be gated on the
            // input device, not on hover: `hover:` alone leaves the hidden base state standing on a
            // phone, and the week view stacks these same columns at phone width, where this button is
            // the only way to add anything. Opacity rather than display so the slot keeps its height —
            // the grid must not twitch as the cursor crosses it — and so the button stays tabbable,
            // which `group-focus-within` then reveals.
            'pointer-fine:opacity-0',
            'pointer-fine:group-hover:opacity-100 pointer-fine:group-focus-within:opacity-100',
            compact ? 'h-6' : 'h-7',
          )}
        >
          <Plus className="h-3.5 w-3.5" />
        </button>
      )}
    </div>
  )
}
