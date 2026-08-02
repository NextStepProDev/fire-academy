import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import { Scale, TrendingDown, TrendingUp, TriangleAlert } from 'lucide-react'
import { adminApi } from '../../api/admin'
import { myTrainingApi } from '../../api/user'
import { Button } from '../ui/Button'
import { ConfirmDialog } from '../ui/ConfirmDialog'
import { LoadingSpinner } from '../ui/LoadingSpinner'
import { WeightChart } from './WeightChart'
import { addDaysIso, formatLongDate, todayIso } from '../../utils/calendarRange'
import { keepWithinEntity } from '../../utils/queryEntity'
import type { WeightPoint, WeightRange } from '../../types'

/**
 * How far back a weigh-in may be dated: the server sends 120 days of history, and a reading saved
 * outside that window would exist without ever appearing anywhere.
 */
const CHARTED_DAYS = 120

function earliestChartedDay(): string {
  return addDaysIso(todayIso(), -(CHARTED_DAYS - 1))
}

/**
 * Morning weigh-ins.
 * <p>
 * Only the client can record — the coach reads. That is not a permission subtlety but the shape of
 * the thing: nobody else is standing on the scale, and a coach-entered number would quietly become
 * a second source of truth.
 */
export function WeightPanel({ athleteId }: { athleteId: string | null }) {
  const { t } = useTranslation('calendar')
  const queryClient = useQueryClient()
  const isCoach = athleteId != null

  const [draft, setDraft] = useState('')
  // Defaults to this morning and returns there after every save. Backfilling a missed day is the
  // exception; a date left silently pointing at last Tuesday would overwrite the wrong day.
  const [date, setDate] = useState(todayIso())
  const [toDelete, setToDelete] = useState<WeightPoint | null>(null)
  const [error, setError] = useState<string | null>(null)

  // The window is part of what is being fetched, so it belongs in the key — switching range must
  // not read the previous range's rows out of cache.
  const [range, setRange] = useState<WeightRange>('QUARTER')
  const queryKey = isCoach
    ? ['admin', 'weights', athleteId, range]
    : ['user', 'my-training', 'weights', range]

  const weightsQuery = useQuery({
    queryKey,
    queryFn: () => (isCoach
      ? adminApi.getAthleteWeights(athleteId, range)
      : myTrainingApi.getWeights(range)),
    staleTime: 0,
    refetchOnMount: 'always',
    // Widening the window keeps the chart on screen — it is the same person, seen further back. The
    // person is not negotiable: one client's readings must never stand under another client's name,
    // so the placeholder stops at the trailing range parameter.
    placeholderData: (previous, previousQuery) =>
      keepWithinEntity(previous, previousQuery, queryKey, 1),
  })

  const recordMutation = useMutation({
    mutationFn: (body: { weightKg: number; date: string }) => myTrainingApi.recordWeight(body),
    onSuccess: () => {
      setDraft('')
      setDate(todayIso())
      setError(null)
      void queryClient.invalidateQueries({ queryKey })
    },
    onError: (e: Error) => setError(e.message),
  })

  // A wrong number can be corrected by saving over the day, but a reading for a day that never
  // happened has no correction — it has to be removable.
  const deleteMutation = useMutation({
    mutationFn: (day: string) => myTrainingApi.deleteWeight(day),
    onSuccess: () => {
      setToDelete(null)
      void queryClient.invalidateQueries({ queryKey })
    },
    onError: (e: Error) => {
      setToDelete(null)
      setError(e.message)
    },
  })

  const submit = (e: FormEvent) => {
    e.preventDefault()
    // Comma is what a Polish keyboard produces for a decimal, and nobody should have to know that
    // the API wants a dot.
    const value = Number.parseFloat(draft.replace(',', '.'))
    if (Number.isNaN(value)) return
    setError(null)
    recordMutation.mutate({ weightKg: value, date })
  }

  if (weightsQuery.isLoading) return <LoadingSpinner />
  const data = weightsQuery.data
  if (!data) return null

  const change = data.weeklyChangePercent == null ? null : Number(data.weeklyChangePercent)
  const existing = data.points.find(p => p.date === date)

  return (
    <section className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h3 className="flex items-center gap-2 text-sm font-medium text-surface-300">
          <Scale className="h-4 w-4 text-primary-400" />
          {t('weight.title')}
        </h3>
        {data.currentTrendKg != null && (
          <span className="flex items-center gap-2 text-sm">
            <span className="text-surface-100 [font-variant-numeric:tabular-nums]">
              {Number(data.currentTrendKg).toFixed(1)} kg
            </span>
            {change != null && change !== 0 && (
              <span className={clsx('inline-flex items-center gap-0.5 text-xs',
                change < 0 ? 'text-surface-400' : 'text-surface-400')}>
                {change < 0 ? <TrendingDown className="h-3 w-3" /> : <TrendingUp className="h-3 w-3" />}
                <span className="[font-variant-numeric:tabular-nums]">
                  {change > 0 ? '+' : ''}{change.toFixed(1)}%
                </span>
                <span className="text-surface-500">{t('weight.perWeek')}</span>
              </span>
            )}
            {/* What the number stands on. A trend off two mornings is still the best guess
                available, but saying so beats letting it pass for a week's worth of data. */}
            <span className="text-xs text-surface-500">
              {t('weight.fromReadings', { count: data.trendReadings })}
            </span>
          </span>
        )}
      </div>

      {/* Coach-only, exactly like the overtraining signal — the field is absent from the client's
          response, so this cannot render for them by accident. */}
      {data.rapidLoss && (
        <p role="alert" className="flex items-start gap-2 rounded-lg border border-amber-500/40 bg-amber-500/10 px-3 py-2 text-sm text-amber-200">
          <TriangleAlert className="mt-0.5 h-4 w-4 shrink-0" />
          {t('weight.rapidLoss')}
        </p>
      )}

      {!isCoach && (
        <form onSubmit={submit} className="flex flex-wrap items-end gap-2">
          <div>
            <label htmlFor="weight-input" className="mb-1 block text-xs text-surface-400">
              {t('weight.todayLabel')}
            </label>
            <input
              id="weight-input"
              inputMode="decimal"
              placeholder="74,2"
              className="w-28 rounded-lg border border-surface-700 bg-surface-800 px-3 py-2 text-surface-100 [font-variant-numeric:tabular-nums] focus:outline-none focus:ring-2 focus:ring-primary-500"
              value={draft}
              onChange={e => setDraft(e.target.value)}
            />
          </div>
          {/* Missing a day should not mean losing it. The floor is the charted window: a reading
              older than that would save and then be invisible, which is worse than refusing it. */}
          <div>
            <label htmlFor="weight-date" className="mb-1 block text-xs text-surface-400">
              {t('weight.dateLabel')}
            </label>
            <input
              id="weight-date"
              type="date"
              max={todayIso()}
              min={earliestChartedDay()}
              className="rounded-lg border border-surface-700 bg-surface-800 px-3 py-2 text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
              value={date}
              onChange={e => setDate(e.target.value)}
            />
          </div>
          <Button type="submit" variant="primary" size="sm"
            loading={recordMutation.isPending} disabled={!draft.trim()}>
            {t('weight.save')}
          </Button>
          {/* Saving over an existing day is a correction by design, but it should be a knowing one. */}
          {existing != null && (
            <p className="basis-full text-xs text-surface-500">
              {t('weight.existingEntry', { value: Number(existing.weightKg).toFixed(1) })}
            </p>
          )}
          {error && (
            <p role="alert" className="basis-full rounded bg-rose-500/10 px-3 py-2 text-sm text-rose-300">
              {error}
            </p>
          )}
        </form>
      )}

      {/* Kept out of the header row: the range belongs to the chart below it, not to the current
          trend above it, which always means "this week" whatever window is on screen. */}
      <div className="flex gap-1" role="group" aria-label={t('weight.rangeGroup')}>
        {(['QUARTER', 'YEAR', 'ALL'] as const).map(option => (
          <button
            key={option}
            type="button"
            aria-pressed={range === option}
            onClick={() => setRange(option)}
            className={clsx(
              'rounded-lg px-2.5 py-1 text-xs font-medium transition-colors',
              range === option
                ? 'border border-primary-500/40 bg-surface-800 text-primary-400'
                : 'text-surface-400 hover:bg-surface-800/50 hover:text-surface-200',
            )}
          >
            {t(`weight.range.${option}`)}
          </button>
        ))}
      </div>

      {data.points.length === 0 ? (
        <p className="text-sm text-surface-500">
          {isCoach ? t('weight.emptyCoach') : t('weight.emptyClient')}
        </p>
      ) : (
        <>
          <WeightChart points={data.points} isStale={weightsQuery.isFetching}
            onDelete={isCoach ? undefined : setToDelete} />
          <p className="text-xs text-surface-500">{t('weight.noiseHint')}</p>
        </>
      )}

      <ConfirmDialog
        isOpen={toDelete !== null}
        onClose={() => setToDelete(null)}
        onConfirm={() => toDelete && deleteMutation.mutate(toDelete.date)}
        title={t('weight.deleteTitle')}
        message={toDelete == null ? '' : t('weight.deleteMessage', {
          date: formatLongDate(toDelete.date),
          value: Number(toDelete.weightKg).toFixed(1),
        })}
        confirmLabel={t('weight.delete')}
        danger
        loading={deleteMutation.isPending}
      />
    </section>
  )
}
