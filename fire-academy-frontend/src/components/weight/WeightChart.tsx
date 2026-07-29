import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { differenceInCalendarDays, format, parseISO } from 'date-fns'
import { pl } from 'date-fns/locale'
import type { WeightPoint } from '../../types'

/**
 * Body weight over time.
 *
 * The trend line is the story and the daily readings are the noise behind it, so they are drawn
 * that way round: a solid 2px line in the series colour, and faint recessive dots for the raw
 * measurements. Reacting to a single day's number in a weight-class sport means chasing water.
 *
 * The series colour is primary-600 rather than the brighter 400/500 the rest of the UI uses —
 * those sit outside the readable lightness band on this dark surface.
 */

const SERIES = '#ea580c'

// Plot geometry in viewBox units; the SVG scales to its container.
const W = 640
const H = 190
const PAD = { top: 10, right: 12, bottom: 22, left: 38 }
const PLOT_W = W - PAD.left - PAD.right
const PLOT_H = H - PAD.top - PAD.bottom

export function WeightChart({ points, isStale }: { points: WeightPoint[]; isStale?: boolean }) {
  const { t } = useTranslation('calendar')
  const [hover, setHover] = useState<number | null>(null)
  const [showTable, setShowTable] = useState(false)

  const model = useMemo(() => build(points), [points])
  if (!model) return null

  const { scaled, trendPath, yTicks, xTicks, lastTrend } = model
  const active = hover != null ? scaled[hover] : null

  return (
    <div className="space-y-2">
      {/* Two marks are on the plot, so identity cannot rest on colour alone. */}
      <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-surface-400">
        <span className="flex items-center gap-3">
          <span className="flex items-center gap-1.5">
            <span className="inline-block h-0.5 w-4 rounded" style={{ background: SERIES }} />
            {t('weight.legendTrend')}
          </span>
          <span className="flex items-center gap-1.5">
            <span className="inline-block h-1.5 w-1.5 rounded-full bg-surface-500" />
            {t('weight.legendDaily')}
          </span>
        </span>
        <button
          type="button"
          onClick={() => setShowTable(v => !v)}
          className="rounded px-1.5 py-0.5 text-surface-400 underline underline-offset-2 hover:text-surface-200"
        >
          {showTable ? t('weight.hideTable') : t('weight.showTable')}
        </button>
      </div>

      {/* Held at reduced opacity while refetching rather than replaced by a skeleton — no jump. */}
      <div className={isStale ? 'relative opacity-60 transition-opacity' : 'relative transition-opacity'}>
        <svg viewBox={`0 0 ${W} ${H}`} className="h-auto w-full" role="img"
          aria-label={t('weight.chartLabel')}>
          {yTicks.map(tick => (
            <g key={tick.value}>
              {/* Hairline, solid, one step off the surface — recessive by design. */}
              <line x1={PAD.left} x2={W - PAD.right} y1={tick.y} y2={tick.y}
                stroke="var(--color-surface-800)" strokeWidth="1" />
              <text x={PAD.left - 6} y={tick.y + 3} textAnchor="end"
                className="fill-surface-500 [font-size:9px] [font-variant-numeric:tabular-nums]">
                {tick.value.toFixed(1)}
              </text>
            </g>
          ))}

          {xTicks.map(tick => (
            <text key={tick.label + tick.x} x={tick.x} y={H - 6} textAnchor="middle"
              className="fill-surface-500 [font-size:9px]">
              {tick.label}
            </text>
          ))}

          {/* Raw readings: deliberately faint and small — they are the noise, not the signal. */}
          {scaled.map(p => (
            <circle key={p.date} cx={p.x} cy={p.yWeight} r="2"
              className="fill-surface-500" opacity="0.65" />
          ))}

          <path d={trendPath} fill="none" stroke={SERIES} strokeWidth="2"
            strokeLinejoin="round" strokeLinecap="round" />

          {lastTrend && (
            <>
              {/* 2px surface ring so the end dot stays legible where it crosses the line. */}
              <circle cx={lastTrend.x} cy={lastTrend.y} r="4.5" fill={SERIES}
                stroke="var(--color-surface-900)" strokeWidth="2" />
              {/* One selective direct label — the current trend — never a number on every point. */}
              <text x={lastTrend.x - 8} y={lastTrend.y - 8} textAnchor="end"
                className="fill-surface-200 [font-size:10px] [font-variant-numeric:tabular-nums]">
                {lastTrend.value.toFixed(1)} kg
              </text>
            </>
          )}

          {active && (
            <line x1={active.x} x2={active.x} y1={PAD.top} y2={PAD.top + PLOT_H}
              stroke="var(--color-surface-700)" strokeWidth="1" />
          )}

          {/*
            One nearest-point overlay instead of per-dot hit targets: at 120 days a dot is ~5px
            wide, and asking anyone to land on that dead-centre is not a hit target.
          */}
          <rect
            x={PAD.left} y={PAD.top} width={PLOT_W} height={PLOT_H} fill="transparent"
            onMouseMove={e => setHover(nearestIndex(e, scaled))}
            onMouseLeave={() => setHover(null)}
          />
        </svg>

        {active && (
          <div
            className="pointer-events-none absolute -translate-x-1/2 -translate-y-full rounded-lg border border-surface-700 bg-surface-800 px-2 py-1 text-xs text-surface-100 shadow-lg"
            style={{ left: `${(active.x / W) * 100}%`, top: `${(active.yWeight / H) * 100}%` }}
          >
            <p className="text-surface-400">{format(parseISO(active.date), 'd MMM', { locale: pl })}</p>
            <p className="[font-variant-numeric:tabular-nums]">{active.weight.toFixed(1)} kg</p>
            {active.trend != null && (
              <p className="text-surface-400 [font-variant-numeric:tabular-nums]">
                {t('weight.legendTrend')}: {active.trend.toFixed(1)} kg
              </p>
            )}
          </div>
        )}
      </div>

      {/* The tooltip enhances; this table is how every value is actually reachable. */}
      {showTable && (
        <div className="max-h-48 overflow-y-auto rounded-lg border border-surface-800">
          <table className="w-full text-sm">
            <thead className="sticky top-0 bg-surface-900 text-left text-xs text-surface-400">
              <tr>
                <th scope="col" className="px-3 py-1.5 font-medium">{t('weight.tableDate')}</th>
                <th scope="col" className="px-3 py-1.5 font-medium">{t('weight.tableWeight')}</th>
                <th scope="col" className="px-3 py-1.5 font-medium">{t('weight.legendTrend')}</th>
              </tr>
            </thead>
            <tbody>
              {[...points].reverse().map(p => (
                <tr key={p.date} className="border-t border-surface-800">
                  <td className="px-3 py-1 text-surface-300">
                    {format(parseISO(p.date), 'd MMM yyyy', { locale: pl })}
                  </td>
                  <td className="px-3 py-1 text-surface-100 [font-variant-numeric:tabular-nums]">
                    {Number(p.weightKg).toFixed(1)}
                  </td>
                  <td className="px-3 py-1 text-surface-400 [font-variant-numeric:tabular-nums]">
                    {p.trendKg == null ? '—' : Number(p.trendKg).toFixed(1)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

interface ScaledPoint {
  date: string
  weight: number
  trend: number | null
  x: number
  yWeight: number
  yTrend: number | null
}

/**
 * The x position follows the CALENDAR, not the reading index: a fortnight's gap has to look like a
 * gap. Spacing readings evenly would quietly redraw a break in logging as continuous progress.
 */
function build(points: WeightPoint[]) {
  if (points.length === 0) return null

  const first = parseISO(points[0].date)
  const span = Math.max(1, differenceInCalendarDays(parseISO(points[points.length - 1].date), first))

  const values = points.flatMap(p => [Number(p.weightKg), p.trendKg == null ? Number(p.weightKg) : Number(p.trendKg)])
  let min = Math.min(...values)
  let max = Math.max(...values)
  // Weight legitimately does not start at zero — the interesting range is a couple of kilos wide.
  // Pad it so the line never touches the frame, and keep a floor so a flat series is not a
  // dramatic zigzag through rounding.
  const padding = Math.max(0.6, (max - min) * 0.2)
  min -= padding
  max += padding

  const x = (date: string) => PAD.left + (differenceInCalendarDays(parseISO(date), first) / span) * PLOT_W
  const y = (value: number) => PAD.top + PLOT_H - ((value - min) / (max - min)) * PLOT_H

  const scaled: ScaledPoint[] = points.map(p => ({
    date: p.date,
    weight: Number(p.weightKg),
    trend: p.trendKg == null ? null : Number(p.trendKg),
    x: x(p.date),
    yWeight: y(Number(p.weightKg)),
    yTrend: p.trendKg == null ? null : y(Number(p.trendKg)),
  }))

  const trendPoints = scaled.filter(p => p.yTrend != null)
  const trendPath = trendPoints
    .map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x.toFixed(1)},${p.yTrend!.toFixed(1)}`)
    .join(' ')

  const last = trendPoints.at(-1)
  const lastTrend = last ? { x: last.x, y: last.yTrend!, value: last.trend! } : null

  const yTicks = [0, 0.5, 1].map(fraction => {
    const value = min + (max - min) * fraction
    return { value, y: y(value) }
  })

  // Two or three date labels: enough to orient, few enough not to collide.
  const xTicks = [0, 0.5, 1]
    .map(fraction => {
      const point = points[Math.round(fraction * (points.length - 1))]
      return { x: x(point.date), label: format(parseISO(point.date), 'd MMM', { locale: pl }) }
    })
    .filter((tick, i, all) => i === 0 || tick.label !== all[i - 1].label)

  return { scaled, trendPath, yTicks, xTicks, lastTrend }
}

function nearestIndex(event: React.MouseEvent<SVGRectElement>, scaled: ScaledPoint[]): number {
  const rect = event.currentTarget.getBoundingClientRect()
  const ratio = (event.clientX - rect.left) / rect.width
  const target = PAD.left + ratio * PLOT_W
  let best = 0
  let bestDistance = Infinity
  scaled.forEach((p, i) => {
    const distance = Math.abs(p.x - target)
    if (distance < bestDistance) {
      bestDistance = distance
      best = i
    }
  })
  return best
}
