import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Wallet, Pin, ChevronDown, ChevronRight } from 'lucide-react'
import { formatDate } from '../../utils/dates'
import { formatMonth } from '../../utils/trainingSchedule'
import type { TrainingUserHistory, TrainingUserSubscription, TrainingUserRefund, TrainingUserPayment } from '../../types'
import clsx from 'clsx'

const money = (n: number | null) => (n == null ? '—' : `${Math.round(n * 100) / 100} zł`)
const sumAmount = (list: TrainingUserPayment[]) => list.reduce((s, p) => s + (p.amount ?? 0), 0)

/**
 * The training body of a client's card — subscriptions, payment history, refunds/settlements and the unused surplus.
 * Presentational (takes the already-fetched history), so it can sit both in the trainings-context card and, as a
 * collapsible section, in the general user profile under the "Users" tab (the one place to reach ANY client).
 */
export function TrainingHistoryPanel({ history: u }: { history: TrainingUserHistory }) {
  const { t } = useTranslation('admin')

  // Payments grouped year → month, keeping the newest-first order of the payload (years and months descending).
  // Only months that actually had a payment appear. Each year header carries that year's total training cost.
  const paymentsByYear = useMemo(() => {
    const years = new Map<string, Map<string, TrainingUserPayment[]>>()
    for (const p of u.payments) {
      const year = p.yearMonth.slice(0, 4)
      const months = years.get(year) ?? new Map<string, TrainingUserPayment[]>()
      if (!years.has(year)) years.set(year, months)
      const list = months.get(p.yearMonth) ?? []
      if (!months.has(p.yearMonth)) months.set(p.yearMonth, list)
      list.push(p)
    }
    return years
  }, [u.payments])

  // The most recent year starts open (quick access); older years collapse so a long history stays browsable.
  const [openYears, setOpenYears] = useState<Set<string>>(
    () => new Set(u.payments.length ? [u.payments[0].yearMonth.slice(0, 4)] : []),
  )
  const toggleYear = (y: string) => setOpenYears(prev => {
    const next = new Set(prev)
    if (next.has(y)) next.delete(y); else next.add(y)
    return next
  })

  // Months start collapsed: within an open year you see each month's name + total at a glance, and expand a month
  // only when you want the per-training breakdown — so a long history stays compact.
  const [openMonths, setOpenMonths] = useState<Set<string>>(new Set())
  const toggleMonth = (m: string) => setOpenMonths(prev => {
    const next = new Set(prev)
    if (next.has(m)) next.delete(m); else next.add(m)
    return next
  })

  const schedule = (s: TrainingUserSubscription) =>
    `${t(`days.${s.dayOfWeek}`)} ${s.startTime.slice(0, 5)}${s.endTime ? `–${s.endTime.slice(0, 5)}` : ''}`

  const settlementLabel = (r: TrainingUserRefund) =>
    r.settlementType === 'REFUNDED' ? t('trainingUserDetail.methodRefunded')
      : r.settlementType === 'CREDITED' ? t('trainingUserDetail.methodCredited')
        : r.settlementType === 'MADE_UP' ? t('trainingUserDetail.methodMadeUp')
          : t('trainingUserDetail.pending')

  const reason = (r: TrainingUserRefund) => {
    const base = r.type === 'HOLIDAY' ? t('trainingUserDetail.reasonHoliday') : t('trainingUserDetail.reasonSession')
    return r.label ? `${base} · ${r.label}` : base
  }

  return (
    <div>
      {u.creditBalance > 0 && (
        <span className="inline-flex items-center gap-2 px-3 py-2 mb-6 rounded-xl border border-amber-800/50 bg-amber-900/20 text-amber-300 text-sm">
          <Wallet className="w-4 h-4" /> {t('trainingUserDetail.creditBalance', { amount: money(u.creditBalance) })}
        </span>
      )}

      {/* Subscriptions */}
      <section className="mb-8">
        <h3 className="text-lg font-semibold text-surface-100 mb-3">{t('trainingUserDetail.subscriptionsTitle')}</h3>
        {u.subscriptions.length === 0 ? (
          <p className="text-surface-500 text-sm">{t('trainingUserDetail.noSubscriptions')}</p>
        ) : (
          <div className="space-y-2">
            {u.subscriptions.map(s => (
              <div key={s.enrollmentId} className={clsx('rounded-xl border p-4 bg-surface-900', s.active ? 'border-surface-800' : 'border-surface-800/50 opacity-70')}>
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div className="min-w-0">
                    <p className="text-surface-100 font-medium">{s.trainingName}</p>
                    <p className="text-sm text-surface-400">
                      {schedule(s)}{s.instructorName && ` · ${s.instructorName}`}{s.price != null && ` · ${money(s.price)}`}
                    </p>
                  </div>
                  <span className={clsx('shrink-0 inline-flex items-center px-2 py-0.5 text-xs rounded-full', s.active ? 'bg-emerald-900/30 text-emerald-400' : 'bg-surface-800 text-surface-400')}>
                    {s.active ? t('trainingUserDetail.active') : t('trainingUserDetail.ended')}
                  </span>
                </div>
                <p className="text-xs text-surface-500 mt-1">
                  {s.endMonth
                    ? t('trainingUserDetail.period', { from: formatMonth(s.startMonth), to: formatMonth(s.endMonth) })
                    : t('trainingUserDetail.periodOpen', { from: formatMonth(s.startMonth) })}
                  {' · '}{t('trainingUserDetail.enrolledAt', { date: formatDate(s.enrolledAt) })}
                  {s.billableFrom && ` · ${t('trainingUserDetail.billFrom', { date: formatDate(s.billableFrom) })}`}
                </p>
              </div>
            ))}
          </div>
        )}
      </section>

      {/* Payment history — grouped year → month (only months with a payment), newest first, yearly total on top. */}
      <section className="mb-8">
        <h3 className="text-lg font-semibold text-surface-100 mb-3">{t('trainingUserDetail.paymentsTitle')}</h3>
        {u.payments.length === 0 ? (
          <p className="text-surface-500 text-sm">{t('trainingUserDetail.noPayments')}</p>
        ) : (
          <div className="space-y-2">
            {[...paymentsByYear.entries()].map(([year, months]) => {
              const yearTotal = [...months.values()].reduce((s, list) => s + sumAmount(list), 0)
              const open = openYears.has(year)
              return (
                <div key={year} className="rounded-xl border border-surface-800 bg-surface-900 overflow-hidden">
                  <button type="button" onClick={() => toggleYear(year)} aria-expanded={open} className="w-full flex items-center justify-between gap-3 px-4 py-3 hover:bg-surface-800/40">
                    <span className="flex items-center gap-2 font-medium text-surface-100">
                      {open ? <ChevronDown className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />}
                      {year}
                    </span>
                    <span className="text-sm text-primary-300">{t('trainingUserDetail.yearTotal', { amount: money(yearTotal) })}</span>
                  </button>
                  {open && (
                    <div className="border-t border-surface-800 divide-y divide-surface-800/70">
                      {[...months.entries()].map(([ym, list]) => {
                        const mOpen = openMonths.has(ym)
                        return (
                          <div key={ym}>
                            <button type="button" onClick={() => toggleMonth(ym)} aria-expanded={mOpen} className="w-full flex items-center justify-between gap-3 px-4 py-2.5 hover:bg-surface-800/30">
                              <span className="flex items-center gap-2 text-sm text-surface-300 capitalize">
                                {mOpen ? <ChevronDown className="w-3.5 h-3.5 text-surface-500" /> : <ChevronRight className="w-3.5 h-3.5 text-surface-500" />}
                                {formatMonth(ym)}
                                <span className="text-surface-600 normal-case">· {t('participants.trainingsCount', { count: list.length })}</span>
                              </span>
                              <span className="text-sm font-semibold text-surface-100">{money(sumAmount(list))}</span>
                            </button>
                            {mOpen && (
                              <div className="pl-9 pr-4 pb-3 pt-0.5 space-y-1">
                                {list.map((p, i) => (
                                  <div key={i} className="flex items-start justify-between gap-3 text-sm">
                                    <span className="text-surface-400 min-w-0">
                                      {p.trainingName}
                                      <span className="text-surface-600"> · {t('trainingUserDetail.paidOnInline', { date: formatDate(p.paidAt) })}</span>
                                    </span>
                                    <span className="text-right text-surface-400 shrink-0">
                                      {money(p.amount)}
                                      {p.creditApplied > 0 && <span className="block text-xs text-amber-300">{t('trainingUserDetail.creditUsed', { amount: money(p.creditApplied) })}</span>}
                                      {p.pinned && <span className="flex items-center justify-end gap-1 text-xs text-surface-500"><Pin className="w-3 h-3" />{t('trainingUserDetail.pinned')}</span>}
                                    </span>
                                  </div>
                                ))}
                              </div>
                            )}
                          </div>
                        )
                      })}
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        )}
      </section>

      {/* Refunds & settlements */}
      <section>
        <h3 className="text-lg font-semibold text-surface-100 mb-3">{t('trainingUserDetail.refundsTitle')}</h3>
        {u.refunds.length === 0 ? (
          <p className="text-surface-500 text-sm">{t('trainingUserDetail.noRefunds')}</p>
        ) : (
          <div className="overflow-x-auto rounded-xl border border-surface-800">
            <table className="w-full text-sm whitespace-nowrap">
              <thead className="text-surface-400 bg-surface-900">
                <tr>
                  <th className="text-left font-medium px-4 py-2">{t('trainingUserDetail.sessionCol')}</th>
                  <th className="text-left font-medium px-4 py-2">{t('trainingUserDetail.trainingCol')}</th>
                  <th className="text-left font-medium px-4 py-2">{t('trainingUserDetail.reasonCol')}</th>
                  <th className="text-right font-medium px-4 py-2">{t('trainingUserDetail.amountCol')}</th>
                  <th className="text-left font-medium px-4 py-2">{t('trainingUserDetail.statusCol')}</th>
                </tr>
              </thead>
              <tbody>
                {u.refunds.map((r, i) => (
                  <tr key={i} className="border-t border-surface-800/70">
                    <td className="px-4 py-2 text-surface-300">{formatDate(r.sessionDate)}</td>
                    <td className="px-4 py-2 text-surface-300">{r.trainingName}</td>
                    <td className="px-4 py-2 text-surface-400">{reason(r)}</td>
                    <td className="px-4 py-2 text-right text-surface-200">{money(r.amount)}</td>
                    <td className="px-4 py-2">
                      {r.settledAt ? (
                        <>
                          <span className="text-emerald-400">{settlementLabel(r)}<span className="text-surface-500"> · {formatDate(r.settledAt)}</span></span>
                          {r.settlementType === 'CREDITED' && (
                            <span className="block text-xs text-surface-500">
                              {t('trainingUserDetail.creditTrace', {
                                from: formatMonth(r.sessionDate.slice(0, 7)),
                                to: r.consumedInMonth ? formatMonth(r.consumedInMonth) : t('trainingUserDetail.notYetUsed'),
                              })}
                            </span>
                          )}
                        </>
                      ) : (
                        <span className="inline-flex items-center px-2 py-0.5 text-xs rounded-full bg-amber-900/20 text-amber-300">{t('trainingUserDetail.pending')}</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  )
}
