import { useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import { CalendarDays, ChevronLeft, ChevronRight, X } from 'lucide-react'
import { Button } from '../ui/Button'
import { LoadingSpinner } from '../ui/LoadingSpinner'
import { QueryError } from '../ui/QueryError'
import { DayColumn } from './DayColumn'
import { MonthDotGrid } from './MonthDotGrid'
import { DaySheet } from './DaySheet'
import { TrainingFormModal } from './TrainingFormModal'
import { TrainingDetailModal } from './TrainingDetailModal'
import { DeletedTrainingsBanner } from './DeletedTrainingsBanner'
import { useTrainingClipboard } from '../../hooks/useTrainingClipboard'
import { useCompactViewport } from '../../hooks/useCompactViewport'
import {
  eachDay, formatRangeLabel, rangeFor, shiftAnchor, todayIso, weekdayShort,
  type CalendarView,
} from '../../utils/calendarRange'
import type { CreateTrainingBody, PersonalTraining, RecurringSession } from '../../types'
import type { TrainingCalendarAdapter } from './adapter'

/**
 * The shared 1-on-1 calendar: one component for the coach and the client, told apart only by the
 * adapter it is handed. Anything that differs visually between the two lives on the page around it.
 */
export function TrainingCalendar({ adapter }: { adapter: TrainingCalendarAdapter }) {
  const { t } = useTranslation('calendar')
  const queryClient = useQueryClient()
  const { entry: clipboard, copy, clear: clearClipboard } = useTrainingClipboard()

  const [view, setView] = useState<CalendarView>('week')
  const [anchor, setAnchor] = useState(todayIso())
  const [formDate, setFormDate] = useState<string | null>(null)
  const [editing, setEditing] = useState<PersonalTraining | null>(null)
  const [selected, setSelected] = useState<PersonalTraining | null>(null)
  const [openDay, setOpenDay] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  // The month view swaps component below the sm breakpoint rather than restyling: at phone width a
  // day cell can hold a dot, not a card, so the content moves into a sheet behind a tap.
  const compactViewport = useCompactViewport()
  const dotGrid = view === 'month' && compactViewport

  const range = useMemo(() => rangeFor(view, anchor), [view, anchor])
  const days = useMemo(() => eachDay(range), [range])

  const rangeQuery = useQuery({
    queryKey: adapter.rangeKey(range.from, range.to),
    queryFn: () => adapter.fetchRange(range.from, range.to),
    // The global default is a 5-minute staleTime with keepPreviousData. Both are wrong here: a plan
    // the other side just changed must not be served from cache, and switching athlete or page is a
    // different entity, not fresher data for the same one — showing the previous person's trainings
    // under a new name for a frame is worse than a spinner.
    staleTime: 0,
    refetchOnMount: 'always',
    placeholderData: undefined,
  })

  const byDay = useMemo(() => {
    const map = new Map<string, PersonalTraining[]>()
    for (const training of rangeQuery.data?.trainings ?? []) {
      const list = map.get(training.date)
      if (list) list.push(training)
      else map.set(training.date, [training])
    }
    return map
  }, [rangeQuery.data])

  const recurringByDay = useMemo(() => {
    const map = new Map<string, RecurringSession[]>()
    for (const session of rangeQuery.data?.recurring ?? []) {
      const list = map.get(session.date)
      if (list) list.push(session)
      else map.set(session.date, [session])
    }
    return map
  }, [rangeQuery.data])

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: adapter.rangeKey(range.from, range.to) })
  }

  const markSeenMutation = useMutation({
    mutationFn: () => adapter.markSeen(),
    onSuccess: () => {
      // refetchType 'none' on purpose: the dots the viewer is looking at RIGHT NOW must stay lit for
      // this visit — that is the whole point of them. Marking the cache stale means the next entry
      // fetches a clean page instead.
      void queryClient.invalidateQueries({ queryKey: ['user', 'my-training', 'summary'], refetchType: 'none' })
      void queryClient.invalidateQueries({ queryKey: ['admin', 'athletes'], refetchType: 'none' })
    },
  })

  // Only once the page has genuinely arrived. On a cached revisit React Query reports isSuccess in
  // the same tick, and marking seen before the server has counted the dots clears them before they
  // are ever shown — the exact failure the reference implementation shipped.
  const seenSent = useRef(false)
  const canMarkSeen = rangeQuery.isSuccess && !rangeQuery.isFetching
  useEffect(() => {
    if (!canMarkSeen || seenSent.current) return
    seenSent.current = true
    markSeenMutation.mutate()
    // markSeenMutation is stable for the lifetime of the component
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [canMarkSeen])

  const dismissMutation = useMutation({
    mutationFn: () => adapter.dismissDeletions(),
    onSuccess: refresh,
    onError: (e: Error) => setActionError(e.message),
  })

  const pasteMutation = useMutation({
    mutationFn: ({ date }: { date: string }) =>
      adapter.pasteTraining(clipboard!.trainingId, date, clipboard!.mode),
    onSuccess: () => {
      // A cut is spent once pasted; a copy stays armed so the same session can be laid out repeatedly.
      if (clipboard?.mode === 'MOVE') clearClipboard()
      setActionError(null)
      refresh()
    },
    onError: (e: Error) => {
      // The source may have been deleted from another tab while the clipboard was armed.
      setActionError(e.message)
      clearClipboard()
    },
  })

  const submitForm = async (body: CreateTrainingBody) => {
    if (editing) {
      await adapter.updateTraining(editing.id, { ...body, version: editing.version })
    } else {
      await adapter.createTraining(body)
    }
    refresh()
  }

  const gridCols = 'grid gap-2 grid-cols-1 sm:grid-cols-7'

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-1">
          <Button variant="ghost" size="sm" aria-label={t('nav.previous')}
            onClick={() => setAnchor(a => shiftAnchor(view, a, -1))}>
            <ChevronLeft className="h-4 w-4" />
          </Button>
          <span className="min-w-44 text-center text-sm font-medium text-surface-100">
            {formatRangeLabel(view, anchor)}
          </span>
          <Button variant="ghost" size="sm" aria-label={t('nav.next')}
            onClick={() => setAnchor(a => shiftAnchor(view, a, 1))}>
            <ChevronRight className="h-4 w-4" />
          </Button>
          <Button variant="ghost" size="sm" onClick={() => setAnchor(todayIso())}>
            {t('nav.today')}
          </Button>
        </div>

        <div className="flex gap-1" role="group" aria-label={t('nav.viewSwitch')}>
          {(['week', 'month'] as const).map(v => (
            <button
              key={v}
              type="button"
              aria-pressed={view === v}
              onClick={() => setView(v)}
              className={clsx(
                'rounded-lg px-3 py-1.5 text-sm font-medium transition-colors',
                view === v
                  ? 'border border-primary-500/40 bg-surface-800 text-primary-400'
                  : 'text-surface-400 hover:bg-surface-800/50 hover:text-surface-200',
              )}
            >
              {t(`nav.${v}`)}
            </button>
          ))}
        </div>
      </div>

      {clipboard && (
        <div className="flex items-center justify-between gap-3 rounded-lg border border-primary-600/40 bg-primary-500/10 px-3 py-2 text-sm">
          <span className="text-primary-200">
            {t(clipboard.mode === 'MOVE' ? 'clipboard.cut' : 'clipboard.copied', { title: clipboard.title })}
          </span>
          <button type="button" onClick={clearClipboard}
            className="flex h-6 w-6 items-center justify-center rounded text-primary-300 hover:text-primary-100"
            aria-label={t('clipboard.cancel')}>
            <X className="h-4 w-4" />
          </button>
        </div>
      )}

      {actionError && (
        <p role="alert" className="rounded-lg bg-rose-500/10 px-3 py-2 text-sm text-rose-300">{actionError}</p>
      )}

      <DeletedTrainingsBanner
        deletions={rangeQuery.data?.deletions ?? []}
        onDismiss={() => dismissMutation.mutate()}
        dismissing={dismissMutation.isPending}
      />

      {rangeQuery.isLoading ? (
        <LoadingSpinner />
      ) : rangeQuery.isError ? (
        <QueryError error={rangeQuery.error as Error} onRetry={refresh} />
      ) : (
        <div className={clsx('transition-opacity', rangeQuery.isFetching && 'opacity-60')}>
          {dotGrid ? (
            <MonthDotGrid
              days={days}
              anchor={anchor}
              byDay={byDay}
              recurringByDay={recurringByDay}
              pasteArmed={clipboard != null}
              onSelectDay={setOpenDay}
              labels={{
                openDay: t('day.open'),
                unread: t('unread.dot'),
                pasteHere: t('clipboard.pasteHere'),
              }}
            />
          ) : (
        <>
          {view === 'month' && (
            // Weekday names once above the grid; repeating them in 42 cells would be noise.
            <div className={clsx(gridCols, 'mb-1 hidden sm:grid')}>
              {days.slice(0, 7).map(d => (
                <span key={d} className="px-1 text-center text-xs uppercase text-surface-500">
                  {weekdayShort(d)}
                </span>
              ))}
            </div>
          )}
          <div className={gridCols}>
            {days.map(date => (
              <DayColumn
                key={date}
                date={date}
                anchor={anchor}
                trainings={byDay.get(date) ?? []}
                recurring={recurringByDay.get(date) ?? []}
                compact={view === 'month'}
                showWeekday={view === 'week'}
                cutId={clipboard?.mode === 'MOVE' ? clipboard.trainingId : null}
                pasteArmed={clipboard != null}
                onOpen={setSelected}
                onAdd={d => { setEditing(null); setFormDate(d) }}
                onPaste={d => pasteMutation.mutate({ date: d })}
                onCopy={tr => copy({ trainingId: tr.id, title: tr.title, mode: 'COPY' })}
                onCut={tr => copy({ trainingId: tr.id, title: tr.title, mode: 'MOVE' })}
                labels={{
                  add: t('day.add'),
                  copy: t('clipboard.copy'),
                  cut: t('clipboard.cutAction'),
                  pasteHere: t('clipboard.pasteHere'),
                  unread: t('unread.dot'),
                  comments: t('comments.title'),
                  recurring: t('recurring.badge'),
                  task: t('form.kind.TASK'),
                  calories: t('form.calories'),
                }}
              />
            ))}
          </div>
        </>
          )}
        </div>
      )}

      {rangeQuery.data && rangeQuery.data.trainings.length === 0
        && rangeQuery.data.recurring.length === 0 && !rangeQuery.isFetching && (
        <p className="flex items-center gap-2 text-sm text-surface-500">
          <CalendarDays className="h-4 w-4" />
          {t('empty')}
        </p>
      )}

      {openDay != null && (
        <DaySheet
          date={openDay}
          trainings={byDay.get(openDay) ?? []}
          recurring={recurringByDay.get(openDay) ?? []}
          pasteArmed={clipboard != null}
          onClose={() => setOpenDay(null)}
          onOpen={setSelected}
          onAdd={d => { setEditing(null); setFormDate(d) }}
          onPaste={d => pasteMutation.mutate({ date: d })}
          onCopy={tr => copy({ trainingId: tr.id, title: tr.title, mode: 'COPY' })}
          onCut={tr => copy({ trainingId: tr.id, title: tr.title, mode: 'MOVE' })}
          labels={{
            add: t('day.add'),
            copy: t('clipboard.copy'),
            cut: t('clipboard.cutAction'),
            pasteHere: t('clipboard.pasteHere'),
            unread: t('unread.dot'),
            comments: t('comments.title'),
            recurring: t('recurring.badge'),
            task: t('form.kind.TASK'),
            calories: t('form.calories'),
            empty: t('day.empty'),
          }}
        />
      )}

      {formDate != null && (
        <TrainingFormModal
          key={editing?.id ?? `new-${formDate}`}
          training={editing}
          date={formDate}
          onClose={() => { setFormDate(null); setEditing(null) }}
          onSubmit={submitForm}
          onConflictRefresh={() => { refresh(); setFormDate(null); setEditing(null) }}
          canAttach={adapter.role === 'coach'}
        />
      )}

      {selected && (
        <TrainingDetailModal
          key={selected.id}
          training={selected}
          onClose={() => setSelected(null)}
          adapter={adapter}
          onEdit={tr => { setSelected(null); setEditing(tr); setFormDate(tr.date) }}
          onDelete={async tr => { await adapter.deleteTraining(tr.id); refresh() }}
          onDuplicate={async tr => { await adapter.duplicateTraining(tr.id); refresh() }}
          onComplete={adapter.completeTraining
            ? async (tr, rpe, feedback) => {
                await adapter.completeTraining!(tr.id, { rpe, feedback })
                refresh()
                setSelected(null)
              }
            : undefined}
          onUncomplete={adapter.uncompleteTraining
            ? async tr => {
                await adapter.uncompleteTraining!(tr.id)
                refresh()
                setSelected(null)
              }
            : undefined}
        />
      )}
    </div>
  )
}
