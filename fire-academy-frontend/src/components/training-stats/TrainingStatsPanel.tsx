import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import clsx from 'clsx'
import { Award, Flame, TrendingDown, TrendingUp, TriangleAlert } from 'lucide-react'
import { addDays, format, parseISO, startOfWeek, subDays } from 'date-fns'
import { adminApi } from '../../api/admin'
import { myTrainingApi } from '../../api/user'
import { LoadingSpinner } from '../ui/LoadingSpinner'
import { HEAT_CLASSES, HEAT_LEGEND, heatLevel } from './heatmapScale'
import { todayIso } from '../../utils/calendarRange'
import type { TrainingStats } from '../../types'

/** Badge thresholds are computed here from the totals — the server sends numbers, not decorations. */
const TOTAL_MILESTONES = [10, 25, 50, 100, 250]
const STREAK_MILESTONES = [4, 8, 12]

const HEATMAP_WEEKS = 53

/**
 * Statistics under the calendar.
 * <p>
 * Same numbers for both roles; only the overtraining warning differs, and it differs because the
 * server omits the field for the client rather than because this component hides it.
 */
export function TrainingStatsPanel({ athleteId }: { athleteId: string | null }) {
  const { t } = useTranslation('calendar')
  const isCoach = athleteId != null

  const statsQuery = useQuery({
    queryKey: isCoach ? ['admin', 'training-stats', athleteId] : ['user', 'my-training', 'stats'],
    queryFn: () => (isCoach ? adminApi.getAthleteStats(athleteId) : myTrainingApi.getStats()),
    // Unticking a session has to move these numbers at once, or nobody trusts them.
    staleTime: 0,
    refetchOnMount: 'always',
  })

  if (statsQuery.isLoading) return <LoadingSpinner />
  const stats = statsQuery.data
  if (!stats) return null

  const trend = stats.thisMonthCount - stats.prevMonthCount

  return (
    <section className="space-y-4">
      <h3 className="text-sm font-medium text-surface-300">{t('stats.title')}</h3>

      {stats.overtraining && (
        // Coach-only: a page telling a client they are overreaching turns a conversation starter
        // into a verdict.
        <p role="alert" className="flex items-start gap-2 rounded-lg border border-amber-500/40 bg-amber-500/10 px-3 py-2 text-sm text-amber-200">
          <TriangleAlert className="mt-0.5 h-4 w-4 shrink-0" />
          {t('stats.overtraining')}
        </p>
      )}

      <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
        <Tile label={t('stats.thisMonth')} value={String(stats.thisMonthCount)}
          hint={trend === 0 ? undefined : (
            <span className={clsx('inline-flex items-center gap-0.5', trend > 0 ? 'text-emerald-400' : 'text-surface-400')}>
              {trend > 0 ? <TrendingUp className="h-3 w-3" /> : <TrendingDown className="h-3 w-3" />}
              {trend > 0 ? `+${trend}` : trend}
            </span>
          )} />
        <Tile label={t('stats.total')} value={String(stats.totalCount)} />
        <Tile label={t('stats.streak')} value={t('stats.weeks', { count: stats.currentStreakWeeks })}
          hint={stats.bestStreakWeeks > 0
            ? <span className="text-surface-400">{t('stats.best', { count: stats.bestStreakWeeks })}</span>
            : undefined} />
        <Tile
          label={t('stats.attendance')}
          value={stats.attendancePercent == null ? '—' : `${stats.attendancePercent}%`}
          hint={<span className="text-surface-400">{t('stats.attendanceWindow')}</span>}
        />
      </div>

      <Heatmap heatmap={stats.heatmap} label={t('stats.heatmap')} legendLabel={t('stats.heatmapLegend')} />

      <div className="grid gap-3 sm:grid-cols-2">
        <RpeDistribution stats={stats} />
        <TypeBreakdown stats={stats} />
      </div>

      <Badges stats={stats} />
    </section>
  )
}

function Tile({ label, value, hint }: { label: string; value: string; hint?: React.ReactNode }) {
  return (
    <div className="rounded-lg border border-surface-800 bg-surface-900/60 p-3">
      <p className="text-xs uppercase tracking-wide text-surface-500">{label}</p>
      <p className="mt-1 text-xl font-semibold text-surface-100">{value}</p>
      {hint && <p className="mt-0.5 text-xs">{hint}</p>}
    </div>
  )
}

function Heatmap({ heatmap, label, legendLabel }: {
  heatmap: Record<string, number>
  label: string
  legendLabel: string
}) {
  // Columns are ISO weeks, so each row is one weekday and the shape reads like a calendar.
  const weeks = useMemo(() => {
    const end = parseISO(todayIso())
    const firstMonday = startOfWeek(subDays(end, (HEATMAP_WEEKS - 1) * 7), { weekStartsOn: 1 })
    return Array.from({ length: HEATMAP_WEEKS }, (_, w) =>
      Array.from({ length: 7 }, (_, d) => format(addDays(firstMonday, w * 7 + d), 'yyyy-MM-dd')))
  }, [])

  return (
    <div>
      <div className="mb-1 flex items-center justify-between">
        <p className="text-xs text-surface-400">{label}</p>
        <div className="flex items-center gap-1.5 text-[11px] text-surface-500">
          <span>{legendLabel}</span>
          {HEAT_LEGEND.map(({ level, label: swatch }) => (
            <span key={level} className="flex items-center gap-0.5">
              <span className={clsx('inline-block h-2.5 w-2.5 rounded-sm', HEAT_CLASSES[level])} />
              {swatch}
            </span>
          ))}
        </div>
      </div>
      <div className="overflow-x-auto">
        <div className="flex gap-0.5">
          {weeks.map((week, i) => (
            <div key={i} className="flex flex-col gap-0.5">
              {week.map(day => (
                <span
                  key={day}
                  title={`${day}: ${heatmap[day] ?? 0}`}
                  className={clsx('h-2.5 w-2.5 rounded-sm', HEAT_CLASSES[heatLevel(heatmap[day] ?? 0)])}
                />
              ))}
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

function RpeDistribution({ stats }: { stats: TrainingStats }) {
  const { t } = useTranslation('calendar')
  const { light, medium, hard } = stats.rpeDistribution
  const total = light + medium + hard

  return (
    <div className="rounded-lg border border-surface-800 bg-surface-900/60 p-3">
      <p className="text-xs uppercase tracking-wide text-surface-500">{t('stats.intensity')}</p>
      {total === 0 ? (
        <p className="mt-2 text-sm text-surface-500">{t('stats.noRatings')}</p>
      ) : (
        <>
          <div className="mt-2 flex h-3 overflow-hidden rounded-full">
            <span className="bg-emerald-600" style={{ width: `${(light / total) * 100}%` }} />
            <span className="bg-amber-600" style={{ width: `${(medium / total) * 100}%` }} />
            <span className="bg-rose-600" style={{ width: `${(hard / total) * 100}%` }} />
          </div>
          <div className="mt-2 flex flex-wrap gap-3 text-xs text-surface-400">
            <span>{t('stats.light')}: {light}</span>
            <span>{t('stats.medium')}: {medium}</span>
            <span>{t('stats.hard')}: {hard}</span>
          </div>
        </>
      )}
      {stats.avgRpeRecent != null && (
        <p className="mt-2 text-xs text-surface-400">
          {t('stats.avgRpeRecent', { value: stats.avgRpeRecent })}
        </p>
      )}
    </div>
  )
}

function TypeBreakdown({ stats }: { stats: TrainingStats }) {
  const { t } = useTranslation('calendar')
  return (
    <div className="rounded-lg border border-surface-800 bg-surface-900/60 p-3">
      <p className="text-xs uppercase tracking-wide text-surface-500">{t('stats.byType')}</p>
      <dl className="mt-2 space-y-1 text-sm">
        <div className="flex justify-between">
          <dt className="text-surface-400">{t('stats.typePersonal')}</dt>
          <dd className="text-surface-100">{stats.byType.personal}</dd>
        </div>
        <div className="flex justify-between">
          <dt className="text-surface-400">{t('stats.typeRecurring')}</dt>
          <dd className="text-surface-100">{stats.byType.recurring}</dd>
        </div>
      </dl>
      {stats.avgPerMonth != null && (
        <p className="mt-2 text-xs text-surface-400">{t('stats.avgPerMonth', { value: stats.avgPerMonth })}</p>
      )}
    </div>
  )
}

function Badges({ stats }: { stats: TrainingStats }) {
  const { t } = useTranslation('calendar')
  const earnedTotals = TOTAL_MILESTONES.filter(m => stats.totalCount >= m)
  const earnedStreaks = STREAK_MILESTONES.filter(m => stats.bestStreakWeeks >= m)
  const nextTotal = TOTAL_MILESTONES.find(m => m > stats.totalCount)

  if (earnedTotals.length === 0 && earnedStreaks.length === 0 && nextTotal == null) return null

  return (
    <div className="flex flex-wrap items-center gap-2">
      {earnedTotals.map(m => (
        <span key={`t${m}`}
          className="inline-flex items-center gap-1 rounded-full bg-primary-500/15 px-2 py-0.5 text-xs text-primary-300">
          <Award className="h-3.5 w-3.5" />
          {t('stats.badgeTotal', { count: m })}
        </span>
      ))}
      {earnedStreaks.map(m => (
        <span key={`s${m}`}
          className="inline-flex items-center gap-1 rounded-full bg-amber-500/15 px-2 py-0.5 text-xs text-amber-300">
          <Flame className="h-3.5 w-3.5" />
          {t('stats.badgeStreak', { count: m })}
        </span>
      ))}
      {nextTotal != null && (
        <span className="text-xs text-surface-500">
          {t('stats.nextBadge', { count: nextTotal - stats.totalCount })}
        </span>
      )}
    </div>
  )
}
