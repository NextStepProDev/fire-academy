import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import { Scale, TrendingDown, TrendingUp, TriangleAlert } from 'lucide-react'
import { adminApi } from '../../api/admin'
import { myTrainingApi } from '../../api/user'
import { Button } from '../ui/Button'
import { LoadingSpinner } from '../ui/LoadingSpinner'
import { WeightChart } from './WeightChart'

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
  const [error, setError] = useState<string | null>(null)

  const queryKey = isCoach ? ['admin', 'weights', athleteId] : ['user', 'my-training', 'weights']

  const weightsQuery = useQuery({
    queryKey,
    queryFn: () => (isCoach ? adminApi.getAthleteWeights(athleteId) : myTrainingApi.getWeights()),
    staleTime: 0,
    refetchOnMount: 'always',
  })

  const recordMutation = useMutation({
    mutationFn: (weightKg: number) => myTrainingApi.recordWeight({ weightKg }),
    onSuccess: () => {
      setDraft('')
      setError(null)
      void queryClient.invalidateQueries({ queryKey })
    },
    onError: (e: Error) => setError(e.message),
  })

  const submit = (e: FormEvent) => {
    e.preventDefault()
    // Comma is what a Polish keyboard produces for a decimal, and nobody should have to know that
    // the API wants a dot.
    const value = Number.parseFloat(draft.replace(',', '.'))
    if (Number.isNaN(value)) return
    setError(null)
    recordMutation.mutate(value)
  }

  if (weightsQuery.isLoading) return <LoadingSpinner />
  const data = weightsQuery.data
  if (!data) return null

  const change = data.weeklyChangePercent == null ? null : Number(data.weeklyChangePercent)

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
          <Button type="submit" variant="primary" size="sm"
            loading={recordMutation.isPending} disabled={!draft.trim()}>
            {t('weight.save')}
          </Button>
          {/*
            Stated at the point of entry, not only in a policy nobody reads. The calendar is
            obviously shared — the coach builds it — but weight is typed in unprompted, into a field
            that looks like a private diary. Somebody might well enter something different if they
            did not know it is read, and that is exactly why they have to be told before they do.
          */}
          <p className="basis-full text-xs text-surface-500">{t('weight.coachSeesThis')}</p>
          {error && (
            <p role="alert" className="basis-full rounded bg-rose-500/10 px-3 py-2 text-sm text-rose-300">
              {error}
            </p>
          )}
        </form>
      )}

      {data.points.length === 0 ? (
        <p className="text-sm text-surface-500">
          {isCoach ? t('weight.emptyCoach') : t('weight.emptyClient')}
        </p>
      ) : (
        <>
          <WeightChart points={data.points} isStale={weightsQuery.isFetching} />
          <p className="text-xs text-surface-500">{t('weight.noiseHint')}</p>
        </>
      )}
    </section>
  )
}
