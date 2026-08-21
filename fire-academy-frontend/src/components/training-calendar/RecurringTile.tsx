import clsx from 'clsx'
import { NotebookPen, Users } from 'lucide-react'
import type { RecurringSession } from '../../types'

/**
 * A group session the client is subscribed to, shown alongside their 1-on-1 plan.
 * <p>
 * Deliberately reads as background rather than as a task: dashed border, dimmer surface, no controls
 * at all. There is nothing to edit — the session is computed from the subscription, so it has no row
 * to change, and offering a button would promise something the API cannot do.
 *
 * `onOpen` is passed for the COACH only, and it still does not contradict that: what it opens is the
 * coach's own private notebook, not an editor for the session. For the client the tile stays exactly
 * as it was — no click target, no hint that one exists.
 *
 * While the clipboard is armed the caller withholds `onOpen`, exactly as the training cards go inert:
 * the whole day is the drop target then, and one tap must not both paste and open something.
 */
export function RecurringTile({
  session, compact = false, label, onOpen, openLabel, hasNote = false, noteLabel,
}: {
  session: RecurringSession
  compact?: boolean
  label: string
  onOpen?: () => void
  openLabel?: string
  hasNote?: boolean
  noteLabel?: string
}) {
  const Tag = onOpen ? 'button' : 'div'
  return (
    <Tag
      type={onOpen ? 'button' : undefined}
      onClick={onOpen}
      aria-label={onOpen ? openLabel : undefined}
      className={clsx(
        'w-full rounded-lg border border-dashed border-surface-700 bg-surface-900/60 text-left',
        onOpen && 'cursor-pointer hover:border-surface-600 hover:bg-surface-800/60',
        compact ? 'px-1.5 py-1' : 'p-2',
      )}
    >
      <span className="flex items-center gap-1.5">
        <Users className="h-3.5 w-3.5 shrink-0 text-surface-500" />
        {hasNote && (
          <NotebookPen aria-label={noteLabel} className="h-3.5 w-3.5 shrink-0 text-amber-400" />
        )}
        <span className={clsx('truncate text-surface-300', compact ? 'text-xs' : 'text-sm')}>
          {session.name}
        </span>
      </span>
      <span className={clsx('block text-surface-500', compact ? 'text-[11px]' : 'text-xs')}>
        {session.startTime.slice(0, 5)}
        {session.endTime ? `–${session.endTime.slice(0, 5)}` : ''}
        {!compact && session.instructorName ? ` · ${session.instructorName}` : ''}
      </span>
      {!compact && (
        <span className="mt-1 inline-block rounded-full bg-surface-800 px-1.5 py-0.5 text-[11px] text-surface-400">
          {label}
        </span>
      )}
    </Tag>
  )
}
