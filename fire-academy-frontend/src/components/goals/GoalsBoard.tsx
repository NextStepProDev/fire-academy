import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import { Check, Pencil, Plus, RotateCcw, Target, Trash2, Trophy } from 'lucide-react'
import { differenceInCalendarDays, parseISO } from 'date-fns'
import { adminApi } from '../../api/admin'
import { myTrainingApi } from '../../api/user'
import { Button } from '../ui/Button'
import { Modal } from '../ui/Modal'
import { ConfirmDialog } from '../ui/ConfirmDialog'
import { formatLongDate, todayIso } from '../../utils/calendarRange'
import type { AthleteGoal, GoalHorizon, GoalKind, AthleteGoals } from '../../types'

const HORIZONS: GoalHorizon[] = ['SHORT', 'MEDIUM', 'LONG']

/** A freshly reached goal keeps its card for a week before the slot goes back to "set a new one". */
const CELEBRATION_DAYS = 7

const inputClass = 'w-full px-3 py-2 bg-surface-800 border border-surface-700 rounded-lg text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500'

/**
 * Three goal cards above the calendar, plus the trophy case behind them.
 * <p>
 * The coach writes; the client reads. Which of the two this is comes from `athleteId` being present
 * — the same shape as the calendar adapter, so there is no second notion of "am I allowed".
 */
export function GoalsBoard({ athleteId }: { athleteId: string | null }) {
  const { t } = useTranslation('calendar')
  const queryClient = useQueryClient()
  const isCoach = athleteId != null

  const [editing, setEditing] =
    useState<{ goal: AthleteGoal | null; horizon: GoalHorizon; kind: GoalKind } | null>(null)
  const [toDelete, setToDelete] = useState<AthleteGoal | null>(null)
  const [toAchieve, setToAchieve] = useState<AthleteGoal | null>(null)
  const [chestOpen, setChestOpen] = useState(false)

  const goalsQuery = useQuery({
    queryKey: isCoach ? ['admin', 'goals', athleteId] : ['user', 'my-training', 'goals'],
    queryFn: () => (isCoach ? adminApi.getAthleteGoals(athleteId) : myTrainingApi.getGoals()),
    staleTime: 0,
    refetchOnMount: 'always',
  })

  const invalidate = () => queryClient.invalidateQueries({
    queryKey: isCoach ? ['admin', 'goals', athleteId] : ['user', 'my-training', 'goals'],
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => adminApi.deleteAthleteGoal(id),
    onSuccess: () => { setToDelete(null); invalidate() },
  })

  /** Only ever offered for an automatic close — the server refuses the rest. */
  const reopenMutation = useMutation({
    mutationFn: (id: string) => adminApi.reopenAthleteGoal(id),
    onSuccess: invalidate,
  })

  // The starting point for a new weight goal, and what the progress bars measure against.
  const weightsQuery = useQuery({
    queryKey: isCoach ? ['admin', 'weights', athleteId] : ['user', 'my-training', 'weights'],
    queryFn: () => (isCoach ? adminApi.getAthleteWeights(athleteId) : myTrainingApi.getWeights()),
    staleTime: 0,
  })
  const currentTrendKg = weightsQuery.data?.currentTrendKg ?? null

  const goals: AthleteGoals = goalsQuery.data ?? { active: [], achieved: [] }
  const today = todayIso()

  // A client whose coach has not set any goals should see nothing here, not three empty boxes
  // telling them so three times. The coach keeps the empty cards — for them they are the way in.
  if (!isCoach && goals.active.length === 0 && goals.achieved.length === 0) {
    return null
  }

  /** Achieved within the celebration window still occupies its horizon's card. */
  const celebrating = goals.achieved.filter(g =>
    g.achievedAt != null && differenceInCalendarDays(parseISO(today), parseISO(g.achievedAt)) <= CELEBRATION_DAYS)

  return (
    <section className="space-y-3">
      <div className="flex items-center justify-between gap-3">
        <h3 className="flex items-center gap-2 text-sm font-medium text-surface-300">
          <Target className="h-4 w-4 text-primary-400" />
          {t('goals.title')}
        </h3>
        {goals.achieved.length > 0 && (
          <button type="button" onClick={() => setChestOpen(true)}
            className="inline-flex items-center gap-1.5 text-sm text-surface-400 transition-colors hover:text-primary-400">
            <Trophy className="h-4 w-4" />
            {t('goals.trophyCase', { count: goals.achieved.length })}
          </button>
        )}
      </div>

      {/*
        Two rows rather than one. A technique goal and a weight goal are different kinds of thing —
        one needs a person to say it happened, the other closes itself off the scale — so they get
        their own slots instead of competing for a single card per horizon.
      */}
      {(['GENERAL', 'WEIGHT'] as const).map(kind => {
        const activeOfKind = goals.active.filter(g => g.kind === kind)
        const freshOfKind = celebrating.filter(g => g.kind === kind)
        // The client sees the weight row only once there is something in it.
        if (!isCoach && activeOfKind.length === 0 && freshOfKind.length === 0) return null
        return (
          <div key={kind} className="space-y-1.5">
            <p className="text-xs uppercase tracking-wide text-surface-500">
              {t(kind === 'WEIGHT' ? 'goals.weightSection' : 'goals.generalSection')}
            </p>
            <div className="grid gap-2 sm:grid-cols-3">
              {HORIZONS.map(horizon => {
                const active = activeOfKind.find(g => g.horizon === horizon)
                const fresh = freshOfKind.find(g => g.horizon === horizon)
                return (
                  <GoalCard
                    key={horizon}
                    horizon={horizon}
                    kind={kind}
                    goal={active ?? fresh ?? null}
                    achieved={active == null && fresh != null}
                    isCoach={isCoach}
                    currentTrendKg={currentTrendKg}
                    onEdit={() => setEditing({ goal: active ?? null, horizon, kind })}
                    onDelete={() => active && setToDelete(active)}
                    onAchieve={() => active && setToAchieve(active)}
                    onReopen={() => fresh && reopenMutation.mutate(fresh.id)}
                  />
                )
              })}
            </div>
          </div>
        )
      })}

      {editing && (
        <GoalFormModal
          key={editing.goal?.id ?? `new-${editing.kind}-${editing.horizon}`}
          athleteId={athleteId!}
          goal={editing.goal}
          horizon={editing.horizon}
          kind={editing.kind}
          currentTrendKg={currentTrendKg}
          onClose={() => setEditing(null)}
          onSaved={() => { setEditing(null); invalidate() }}
        />
      )}

      {toAchieve && (
        <AchieveGoalModal
          goal={toAchieve}
          onClose={() => setToAchieve(null)}
          onSaved={() => { setToAchieve(null); invalidate() }}
        />
      )}

      {chestOpen && (
        <Modal isOpen onClose={() => setChestOpen(false)} title={t('goals.trophyTitle')} size="lg">
          <div className="grid gap-4 sm:grid-cols-3">
            {HORIZONS.map(horizon => (
              <div key={horizon}>
                <h4 className="mb-2 text-sm font-medium text-surface-300">{t(`goals.horizon.${horizon}`)}</h4>
                <ul className="space-y-2">
                  {goals.achieved.filter(g => g.horizon === horizon).map(goal => (
                    <li key={goal.id}
                      className="rounded-lg border border-emerald-500/30 bg-emerald-900/20 px-3 py-2">
                      <p className="text-sm text-surface-100">{goal.content}</p>
                      <p className="mt-1 text-xs text-emerald-400">
                        {goal.achievedAt && formatLongDate(goal.achievedAt)}
                      </p>
                    </li>
                  ))}
                  {goals.achieved.every(g => g.horizon !== horizon) && (
                    <li className="text-sm text-surface-500">{t('goals.trophyEmpty')}</li>
                  )}
                </ul>
              </div>
            ))}
          </div>
        </Modal>
      )}

      <ConfirmDialog
        isOpen={toDelete !== null}
        onClose={() => setToDelete(null)}
        onConfirm={() => toDelete && deleteMutation.mutate(toDelete.id)}
        title={t('goals.deleteTitle')}
        message={t('goals.deleteMessage')}
        confirmLabel={t('goals.delete')}
        danger
        loading={deleteMutation.isPending}
      />
    </section>
  )
}

function GoalCard({
  horizon, kind, goal, achieved, isCoach, currentTrendKg, onEdit, onDelete, onAchieve, onReopen,
}: {
  horizon: GoalHorizon
  kind: GoalKind
  goal: AthleteGoal | null
  achieved: boolean
  isCoach: boolean
  currentTrendKg: number | null
  onEdit: () => void
  onDelete: () => void
  onAchieve: () => void
  onReopen: () => void
}) {
  const { t } = useTranslation('calendar')
  const today = todayIso()
  const daysLeft = goal?.targetDate
    ? differenceInCalendarDays(parseISO(goal.targetDate), parseISO(today))
    : null

  if (!goal) {
    return (
      <div className="rounded-lg border border-dashed border-surface-700 p-3">
        <p className="text-xs uppercase tracking-wide text-surface-500">{t(`goals.horizon.${horizon}`)}</p>
        {isCoach ? (
          <button type="button" onClick={onEdit}
            className="mt-2 inline-flex items-center gap-1 text-sm text-surface-400 transition-colors hover:text-primary-400">
            <Plus className="h-4 w-4" />
            {t('goals.add')}
          </button>
        ) : (
          <p className="mt-2 text-sm text-surface-500">{t('goals.none')}</p>
        )}
      </div>
    )
  }

  const progress = weightProgress(goal, currentTrendKg)

  return (
    <div className={clsx(
      'rounded-lg border p-3',
      achieved
        ? 'border-emerald-500/40 bg-emerald-900/20'
        : daysLeft != null && daysLeft < 0
          ? 'border-surface-800 bg-surface-900/60 opacity-70'
          : 'border-surface-800 bg-surface-900/60',
    )}>
      <div className="flex items-start justify-between gap-2">
        <p className="text-xs uppercase tracking-wide text-surface-500">{t(`goals.horizon.${horizon}`)}</p>
        {achieved && <Trophy className="h-4 w-4 shrink-0 text-emerald-400" />}
      </div>
      <p className="mt-1 text-sm text-surface-100">{goal.content}</p>

      {goal.targetWeightKg != null && (
        <p className="mt-0.5 text-sm text-surface-300 [font-variant-numeric:tabular-nums]">
          {t('goals.targetWeight', { value: Number(goal.targetWeightKg).toFixed(1) })}
        </p>
      )}
      {progress && !achieved && (
        <div className="mt-2">
          <div className="h-1.5 overflow-hidden rounded-full bg-surface-800">
            <div className="h-full rounded-full bg-primary-500" style={{ width: `${progress.percent}%` }} />
          </div>
          <p className="mt-1 text-xs text-surface-400 [font-variant-numeric:tabular-nums]">
            {t('goals.remaining', { value: progress.remaining.toFixed(1) })}
          </p>
        </div>
      )}

      {goal.targetDate && !achieved && (
        <p className={clsx('mt-1 text-xs',
          daysLeft != null && daysLeft < 0 ? 'text-surface-500'
            : daysLeft === 0 ? 'text-amber-400' : 'text-surface-400')}>
          {daysLeft != null && daysLeft < 0
            ? t('goals.overdue')
            : daysLeft === 0
              ? t('goals.dueToday')
              : t('goals.daysLeft', { count: daysLeft ?? 0 })}
        </p>
      )}
      {achieved && goal.achievedAt && (
        <p className="mt-1 text-xs text-emerald-400">{formatLongDate(goal.achievedAt)}</p>
      )}

      {isCoach && !achieved && (
        <div className="mt-2 flex gap-1">
          {/* A weight goal closes itself off the scale, so there is nothing to tick by hand. */}
          {kind === 'GENERAL' && (
            <Button variant="ghost" size="sm" aria-label={t('goals.achieve')} onClick={onAchieve}>
              <Check className="h-4 w-4" />
            </Button>
          )}
          <Button variant="ghost" size="sm" aria-label={t('goals.edit')} onClick={onEdit}>
            <Pencil className="h-4 w-4" />
          </Button>
          <Button variant="ghost" size="sm" aria-label={t('goals.delete')} onClick={onDelete}>
            <Trash2 className="h-4 w-4" />
          </Button>
        </div>
      )}

      {/* Offered only where the machine closed it — a mistyped weigh-in can pull the trend across
          the target, and a coach needs a way back. A person's own decision stays final. */}
      {isCoach && achieved && goal.achievedAutomatically && (
        <Button variant="ghost" size="sm" className="mt-2" onClick={onReopen}>
          <RotateCcw className="mr-1.5 h-4 w-4" />
          {t('goals.reopen')}
        </Button>
      )}
    </div>
  )
}

/**
 * How far along a weight goal is, measured from the weight when it was set. Clamped, because a
 * client who overshoots the target is at 100%, not 140%.
 */
function weightProgress(goal: AthleteGoal, currentTrendKg: number | null) {
  if (goal.targetWeightKg == null || goal.startWeightKg == null || currentTrendKg == null) {
    return null
  }
  const start = Number(goal.startWeightKg)
  const target = Number(goal.targetWeightKg)
  const total = Math.abs(target - start)
  if (total === 0) return null
  const done = Math.abs(currentTrendKg - start)
  return {
    percent: Math.min(100, Math.max(0, Math.round((done / total) * 100))),
    remaining: Math.max(0, Math.abs(target - currentTrendKg)),
  }
}

function GoalFormModal({
  athleteId, goal, horizon, kind, currentTrendKg, onClose, onSaved,
}: {
  athleteId: string
  goal: AthleteGoal | null
  horizon: GoalHorizon
  kind: GoalKind
  currentTrendKg: number | null
  onClose: () => void
  onSaved: () => void
}) {
  const { t } = useTranslation('calendar')
  const [content, setContent] = useState(goal?.content ?? '')
  const [targetDate, setTargetDate] = useState(goal?.targetDate ?? '')
  const [targetWeight, setTargetWeight] = useState(
    goal?.targetWeightKg == null ? '' : String(goal.targetWeightKg))
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (!content.trim() || saving) return
    setError(null)
    setSaving(true)
    try {
      const body = {
        horizon,
        content: content.trim(),
        targetDate: targetDate || null,
        targetWeightKg: kind === 'WEIGHT'
          ? Number.parseFloat(targetWeight.replace(',', '.'))
          : null,
      }
      if (goal) await adminApi.updateAthleteGoal(goal.id, body)
      else await adminApi.createAthleteGoal(athleteId, body)
      onSaved()
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal isOpen onClose={onClose} title={goal ? t('goals.editTitle') : t('goals.addTitle')}>
      <form onSubmit={submit} className="space-y-4">
        {/* The horizon is fixed: a short-term goal that became long-term is a different goal. */}
        <p className="text-sm text-surface-400">{t(`goals.horizon.${horizon}`)}</p>

        <div>
          <label htmlFor="goal-content" className="mb-1 block text-sm text-surface-300">
            {t('goals.content')}
          </label>
          <textarea id="goal-content" className={`${inputClass} min-h-24`} value={content}
            maxLength={500} onChange={e => setContent(e.target.value)} autoFocus />
        </div>

        {kind === 'WEIGHT' && (
          <div>
            <label htmlFor="goal-weight" className="mb-1 block text-sm text-surface-300">
              {t('goals.targetWeightLabel')}
            </label>
            <input id="goal-weight" inputMode="decimal" className={inputClass} placeholder="73,0"
              value={targetWeight} onChange={e => setTargetWeight(e.target.value)} />
            <p className="mt-1 text-xs text-surface-500">
              {currentTrendKg == null
                ? t('goals.weightNeedsStart')
                : t('goals.weightAutoHint', { value: currentTrendKg.toFixed(1) })}
            </p>
          </div>
        )}

        <div>
          <label htmlFor="goal-target" className="mb-1 block text-sm text-surface-300">
            {t('goals.targetDate')}
          </label>
          <input id="goal-target" type="date" className={inputClass} value={targetDate}
            onChange={e => setTargetDate(e.target.value)} />
        </div>

        {error && (
          <p role="alert" className="rounded-lg bg-rose-500/10 px-3 py-2 text-sm text-rose-300">{error}</p>
        )}

        <div className="flex justify-end gap-3">
          <Button type="button" variant="ghost" size="sm" onClick={onClose}>{t('form.cancel')}</Button>
          <Button type="submit" variant="primary" size="sm" loading={saving}
            disabled={!content.trim() || (kind === 'WEIGHT' && !targetWeight.trim())}>
            {t('form.save')}
          </Button>
        </div>
      </form>
    </Modal>
  )
}

function AchieveGoalModal({
  goal, onClose, onSaved,
}: {
  goal: AthleteGoal
  onClose: () => void
  onSaved: () => void
}) {
  const { t } = useTranslation('calendar')
  // Defaults to today but is back-datable: the coach usually notices some days later.
  const [date, setDate] = useState(todayIso())
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (saving) return
    setError(null)
    setSaving(true)
    try {
      await adminApi.achieveAthleteGoal(goal.id, date)
      onSaved()
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal isOpen onClose={onClose} title={t('goals.achieveTitle')}>
      <form onSubmit={submit} className="space-y-4">
        <p className="text-sm text-surface-200">{goal.content}</p>
        <div>
          <label htmlFor="achieve-date" className="mb-1 block text-sm text-surface-300">
            {t('goals.achievedOn')}
          </label>
          <input id="achieve-date" type="date" className={inputClass} value={date}
            max={todayIso()} onChange={e => setDate(e.target.value)} />
        </div>
        {error && (
          <p role="alert" className="rounded-lg bg-rose-500/10 px-3 py-2 text-sm text-rose-300">{error}</p>
        )}
        <div className="flex justify-end gap-3">
          <Button type="button" variant="ghost" size="sm" onClick={onClose}>{t('form.cancel')}</Button>
          <Button type="submit" variant="primary" size="sm" loading={saving}>{t('goals.achieve')}</Button>
        </div>
      </form>
    </Modal>
  )
}
