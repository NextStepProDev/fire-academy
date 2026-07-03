import { useMemo, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Wallet, Coins, CalendarPlus, RotateCcw, ChevronDown, ChevronRight, Phone, AlertTriangle } from 'lucide-react'
import { adminApi } from '../../api/admin'
import { Button } from '../../components/ui/Button'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { useToast } from '../../context/ToastContext'
import { formatMonth } from '../../utils/trainingSchedule'
import type { RefundEntry } from '../../types'
import clsx from 'clsx'

const fmtDate = (iso: string) => { const [y, m, d] = iso.split('-'); return `${d}.${m}.${y}` }

/** Money owed back to subscribers for paid sessions that were later cancelled. */
export function AdminTrainingRefunds() {
  const { t } = useTranslation('admin')
  const { showToast } = useToast()
  const queryClient = useQueryClient()
  const [showHistory, setShowHistory] = useState(false)

  const pendingQuery = useQuery({
    queryKey: ['admin', 'training-refunds', 'pending'],
    queryFn: () => adminApi.getTrainingRefunds(false),
    staleTime: 0,
  })
  const historyQuery = useQuery({
    queryKey: ['admin', 'training-refunds', 'history'],
    queryFn: () => adminApi.getTrainingRefunds(true),
    enabled: showHistory,
  })
  const unconsumedQuery = useQuery({
    queryKey: ['admin', 'training-refunds', 'unconsumed-credit'],
    queryFn: adminApi.getUnconsumedTrainingCredit,
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['admin', 'training-refunds'] })

  const [openUsers, setOpenUsers] = useState<Set<string>>(new Set())
  const toggleUser = (userId: string) =>
    setOpenUsers(prev => { const n = new Set(prev); if (n.has(userId)) n.delete(userId); else n.add(userId); return n })

  const settleMut = useMutation({
    mutationFn: ({ id, type }: { id: string; type: 'REFUNDED' | 'CREDITED' }) => adminApi.settleRefund(id, type),
    onSuccess: () => { invalidate(); showToast(t('trainingRefunds.settled'), 'success') },
    onError: (e: Error) => showToast(e.message, 'error'),
  })
  const settleUserMut = useMutation({
    mutationFn: ({ userId, type }: { userId: string; type: 'REFUNDED' | 'CREDITED' }) => adminApi.settleUserRefunds(userId, type),
    onSuccess: () => { invalidate(); showToast(t('trainingRefunds.settled'), 'success') },
    onError: (e: Error) => showToast(e.message, 'error'),
  })
  const unsettleMut = useMutation({
    mutationFn: adminApi.unsettleRefund,
    onSuccess: () => { invalidate(); showToast(t('trainingRefunds.unsettled'), 'success') },
    onError: (e: Error) => showToast(e.message, 'error'),
  })

  const reason = (r: RefundEntry) =>
    r.type === 'HOLIDAY'
      ? (r.label ? `${t('trainingRefunds.reasonHoliday')} · ${r.label}` : t('trainingRefunds.reasonHoliday'))
      : t('trainingRefunds.reasonSession')
  const methodLabel = (r: RefundEntry) =>
    r.settlementType === 'CREDITED' ? t('trainingRefunds.methodCredited') : t('trainingRefunds.methodRefunded')

  const pending = useMemo(() => pendingQuery.data ?? [], [pendingQuery.data])
  const total = pending.reduce((sum, r) => sum + r.amount, 0)

  // Group the pending refunds by person, so the admin can settle everything for someone at once,
  // or expand to decide each cancelled session separately.
  const groups = useMemo(() => {
    const map = new Map<string, { userId: string; firstName: string; lastName: string; email: string; phone: string; refunds: RefundEntry[]; total: number }>()
    for (const r of pending) {
      const g = map.get(r.userId) ?? { userId: r.userId, firstName: r.firstName, lastName: r.lastName, email: r.email, phone: r.phone, refunds: [], total: 0 }
      g.refunds.push(r)
      g.total += r.amount
      map.set(r.userId, g)
    }
    return [...map.values()].sort((a, b) => (a.lastName + a.firstName).localeCompare(b.lastName + b.firstName, 'pl'))
  }, [pending])

  return (
    <div>
      <div className="flex items-center gap-2 mb-1">
        <Wallet className="w-5 h-5 text-primary-400" />
        <h2 className="text-xl font-semibold text-surface-100">{t('trainingRefunds.title')}</h2>
      </div>
      <p className="text-sm text-surface-500 mb-4">{t('trainingRefunds.hint')}</p>

      {pendingQuery.isLoading ? (
        <LoadingSpinner />
      ) : !pending.length ? (
        <p className="text-sm text-surface-500">{t('trainingRefunds.empty')}</p>
      ) : (
        <>
          <div className="flex items-center justify-between mb-3">
            <span className="text-sm text-surface-300">{t('trainingRefunds.totalOwed')}</span>
            <span className="text-lg font-semibold text-primary-400">{total} zł</span>
          </div>
          <div className={clsx('space-y-3 transition-opacity', pendingQuery.isFetching && 'opacity-60')}>
            {groups.map(g => {
              const open = openUsers.has(g.userId)
              const busy = settleUserMut.isPending || settleMut.isPending
              return (
                <div key={g.userId} className="rounded-xl border border-surface-800 bg-surface-900 overflow-hidden">
                  {/* Person header: summary + global decision (before expanding) */}
                  <div className="flex flex-wrap items-center gap-3 px-4 py-3">
                    <button onClick={() => toggleUser(g.userId)} className="flex items-center gap-2 min-w-0 flex-1 text-left">
                      {open ? <ChevronDown className="w-4 h-4 text-surface-400 shrink-0" /> : <ChevronRight className="w-4 h-4 text-surface-400 shrink-0" />}
                      <span className="min-w-0">
                        <span className="text-surface-100 font-medium">{g.firstName} {g.lastName}</span>
                        <span className="ml-2 text-sm text-primary-400 font-semibold">{g.total} zł</span>
                        <span className="block text-xs text-surface-500">
                          {t('trainingRefunds.sessionsCount', { count: g.refunds.length })} · {g.email}
                          {g.phone && <span className="inline-flex items-center gap-1"> · <Phone className="w-3 h-3" />{g.phone}</span>}
                        </span>
                      </span>
                    </button>
                    <div className="flex gap-2 shrink-0">
                      <Button variant="primary" size="sm" onClick={() => settleUserMut.mutate({ userId: g.userId, type: 'REFUNDED' })} disabled={busy}>
                        <Coins className="w-3.5 h-3.5 mr-1" />{t('trainingRefunds.settleAllRefunded')}
                      </Button>
                      <Button variant="secondary" size="sm" onClick={() => settleUserMut.mutate({ userId: g.userId, type: 'CREDITED' })} disabled={busy}>
                        <CalendarPlus className="w-3.5 h-3.5 mr-1" />{t('trainingRefunds.settleAllCredited')}
                      </Button>
                    </div>
                  </div>

                  {/* Per-session detail: a separate decision for each cancelled training */}
                  {open && (
                    <div className="border-t border-surface-800 px-4 py-3 space-y-2">
                      {g.refunds.map(r => (
                        <div key={r.id} className="flex flex-wrap items-center justify-between gap-2 rounded-lg bg-surface-800/40 px-3 py-2">
                          <div className="min-w-0 text-sm">
                            <span className="text-surface-200">{r.trainingName}</span>
                            <span className="block text-xs text-surface-500">
                              {fmtDate(r.sessionDate)} · <span className="capitalize">{formatMonth(r.yearMonth)}</span> · {reason(r)}
                            </span>
                          </div>
                          <div className="flex items-center gap-2 shrink-0">
                            <span className="font-medium text-primary-400 text-sm">{r.amount} zł</span>
                            <Button variant="primary" size="sm" onClick={() => settleMut.mutate({ id: r.id, type: 'REFUNDED' })} disabled={busy}>
                              <Coins className="w-3.5 h-3.5 mr-1" />{t('trainingRefunds.settleRefunded')}
                            </Button>
                            <Button variant="secondary" size="sm" onClick={() => settleMut.mutate({ id: r.id, type: 'CREDITED' })} disabled={busy}>
                              <CalendarPlus className="w-3.5 h-3.5 mr-1" />{t('trainingRefunds.settleCredited')}
                            </Button>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        </>
      )}

      {/* Ended subscriptions still sitting on unconsumed CREDITED surplus — nothing applies it automatically once there's no future month left to bill, so it needs a manual cash refund. */}
      {!!unconsumedQuery.data?.length && (
        <div className="mt-6 rounded-xl border border-amber-800/40 bg-amber-950/20 px-4 py-3">
          <div className="flex items-center gap-2 mb-1">
            <AlertTriangle className="w-4 h-4 text-amber-400 shrink-0" />
            <h3 className="text-sm font-semibold text-amber-300">{t('trainingRefunds.unconsumedTitle')}</h3>
          </div>
          <p className="text-xs text-surface-400 mb-3">{t('trainingRefunds.unconsumedHint')}</p>
          <div className="space-y-2">
            {unconsumedQuery.data.map(e => (
              <div key={e.enrollmentId} className="flex flex-wrap items-center justify-between gap-2 rounded-lg bg-surface-900/60 px-3 py-2 text-sm">
                <div className="min-w-0">
                  <span className="text-surface-200">{e.firstName} {e.lastName}</span>
                  <span className="block text-xs text-surface-500">
                    {e.trainingName} · {t('trainingRefunds.unconsumedEndedOn')} <span className="capitalize">{formatMonth(e.endMonth)}</span> · {e.email}
                    {e.phone && <span className="inline-flex items-center gap-1"> · <Phone className="w-3 h-3" />{e.phone}</span>}
                  </span>
                </div>
                <span className="font-semibold text-amber-400 shrink-0">{e.balance} zł</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Settled history */}
      <div className="mt-6 border-t border-surface-800 pt-4">
        <button
          onClick={() => setShowHistory(s => !s)}
          className="flex items-center gap-2 text-sm font-semibold text-surface-300 hover:text-surface-100 transition-colors"
        >
          {showHistory ? <ChevronDown className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />}
          {t('trainingRefunds.historyTitle')}
        </button>
        {showHistory && (
          <div className="mt-3">
            {historyQuery.isLoading ? (
              <LoadingSpinner />
            ) : !historyQuery.data?.length ? (
              <p className="text-sm text-surface-500">{t('trainingRefunds.historyEmpty')}</p>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <tbody>
                    {historyQuery.data.map(r => (
                      <tr key={r.id} className="border-b border-surface-800/50">
                        <td className="py-2 pr-4 text-surface-200">{r.firstName} {r.lastName}</td>
                        <td className="py-2 pr-4 text-surface-400">{r.trainingName} · {fmtDate(r.sessionDate)}</td>
                        <td className="py-2 pr-4 text-surface-300">{r.amount} zł</td>
                        <td className="py-2 pr-4 text-xs">
                          <span className={clsx('px-2 py-0.5 rounded-full', r.settlementType === 'CREDITED' ? 'bg-sky-900/30 text-sky-300' : 'bg-green-900/30 text-green-400')}>
                            {methodLabel(r)}
                          </span>
                          {r.settledAt && <span className="block text-surface-500 mt-0.5">{r.settledAt.slice(0, 10).split('-').reverse().join('.')}</span>}
                        </td>
                        <td className="py-2 text-right">
                          <button onClick={() => unsettleMut.mutate(r.id)} className="inline-flex items-center gap-1 text-xs text-surface-400 hover:text-amber-400" title={t('trainingRefunds.unsettle')}>
                            <RotateCcw className="w-3.5 h-3.5" />{t('trainingRefunds.unsettle')}
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
