import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Wallet, Check, X, ChevronDown, ChevronRight, Phone } from 'lucide-react'
import { adminApi } from '../../api/admin'
import { Button } from '../../components/ui/Button'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { useToast } from '../../context/ToastContext'
import { visibleMonths, formatMonth } from '../../utils/trainingSchedule'
import type { UserMonthlyPayment } from '../../types'
import clsx from 'clsx'

/** A subscriber pays for the MONTH, not per training — this view lets the admin settle everyone's whole month. */
export function AdminTrainingPayments() {
  const { t } = useTranslation('admin')
  const { showToast } = useToast()
  const queryClient = useQueryClient()
  const months = visibleMonths()
  const [month, setMonth] = useState(months[0])
  const [openUsers, setOpenUsers] = useState<Set<string>>(new Set())
  const toggleUser = (id: string) =>
    setOpenUsers(prev => { const n = new Set(prev); if (n.has(id)) n.delete(id); else n.add(id); return n })

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['admin', 'training-payments', month],
    queryFn: () => adminApi.getMonthlyPayments(month),
    staleTime: 0,
  })

  const payMut = useMutation({
    mutationFn: ({ userId, paid }: { userId: string; paid: boolean }) => adminApi.payUserMonth(userId, month, paid),
    onSuccess: (_r, v) => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'training-payments'] })
      queryClient.invalidateQueries({ queryKey: ['admin', 'training-roster'] })
      queryClient.invalidateQueries({ queryKey: ['admin', 'training-refunds'] })
      showToast(t(v.paid ? 'monthlyPayments.paid' : 'monthlyPayments.reverted'), 'success')
    },
    onError: (e: Error) => showToast(e.message, 'error'),
  })

  const users = data ?? []
  const grandTotal = users.filter(u => !u.allPaid).reduce((s, u) => s + u.totalAmount, 0)

  const timeLabel = (l: UserMonthlyPayment['trainings'][number]) =>
    `${l.startTime.slice(0, 5)}${l.endTime ? `–${l.endTime.slice(0, 5)}` : ''}`

  return (
    <div>
      <div className="flex items-center gap-2 mb-1">
        <Wallet className="w-5 h-5 text-primary-400" />
        <h2 className="text-xl font-semibold text-surface-100">{t('monthlyPayments.title')}</h2>
      </div>
      <p className="text-sm text-surface-500 mb-4">{t('monthlyPayments.hint')}</p>

      <div className="flex flex-wrap gap-2 mb-4">
        {months.map(m => (
          <button
            key={m}
            onClick={() => setMonth(m)}
            className={clsx('px-3 py-1.5 text-sm font-medium rounded-lg capitalize transition-colors',
              m === month ? 'bg-primary-600 text-white' : 'bg-surface-900 border border-surface-800 text-surface-300 hover:bg-surface-800')}
          >
            {formatMonth(m)}
          </button>
        ))}
      </div>

      {isLoading ? (
        <LoadingSpinner />
      ) : !users.length ? (
        <p className="text-sm text-surface-500">{t('monthlyPayments.empty')}</p>
      ) : (
        <>
          {grandTotal > 0 && (
            <div className="flex items-center justify-between mb-3">
              <span className="text-sm text-surface-300">{t('monthlyPayments.totalDue')}</span>
              <span className="text-lg font-semibold text-primary-400">{grandTotal} zł</span>
            </div>
          )}
          <div className={clsx('space-y-2 transition-opacity', isFetching && 'opacity-60')}>
            {users.map(u => {
              const open = openUsers.has(u.userId)
              return (
                <div key={u.userId} className={clsx('rounded-xl border bg-surface-900 overflow-hidden',
                  u.allPaid ? 'border-emerald-800/50' : 'border-surface-800')}>
                  <div className="flex flex-wrap items-center gap-3 px-4 py-3">
                    <button onClick={() => toggleUser(u.userId)} className="flex items-center gap-2 min-w-0 flex-1 text-left">
                      {open ? <ChevronDown className="w-4 h-4 text-surface-400 shrink-0" /> : <ChevronRight className="w-4 h-4 text-surface-400 shrink-0" />}
                      <span className="min-w-0">
                        <span className="text-surface-100 font-medium">{u.firstName} {u.lastName}</span>
                        <span className={clsx('ml-2 text-sm font-semibold', u.allPaid ? 'text-emerald-400' : 'text-primary-400')}>{u.totalAmount} zł</span>
                        <span className="block text-xs text-surface-500">
                          {t('monthlyPayments.trainingsCount', { count: u.trainings.length })}
                          {u.creditBalance > 0 && ` · ${t('monthlyPayments.credit', { amount: u.creditBalance })}`}
                          {' · '}{u.phone && <span className="inline-flex items-center gap-1"><Phone className="w-3 h-3" />{u.phone} · </span>}{u.email}
                        </span>
                      </span>
                    </button>
                    {u.allPaid ? (
                      <div className="flex items-center gap-2 shrink-0">
                        <span className="inline-flex items-center gap-1 px-2 py-0.5 text-xs rounded-full bg-emerald-900/30 text-emerald-400"><Check className="w-3.5 h-3.5" />{t('monthlyPayments.paidBadge')}</span>
                        <Button variant="ghost" size="sm" onClick={() => payMut.mutate({ userId: u.userId, paid: false })} disabled={payMut.isPending} className="text-surface-400">
                          {t('monthlyPayments.revert')}
                        </Button>
                      </div>
                    ) : (
                      <Button variant="primary" size="sm" onClick={() => payMut.mutate({ userId: u.userId, paid: true })} disabled={payMut.isPending} className="shrink-0">
                        <Check className="w-3.5 h-3.5 mr-1" />{t('monthlyPayments.pay')}
                      </Button>
                    )}
                  </div>

                  {open && (
                    <div className="border-t border-surface-800 px-4 py-2 space-y-1">
                      {u.trainings.map((l, i) => (
                        <div key={i} className="flex flex-wrap items-center justify-between gap-2 text-sm py-1">
                          <span className="text-surface-300">
                            {t(`days.${l.dayOfWeek}`)} {timeLabel(l)} · {l.trainingName}
                          </span>
                          <span className="flex items-center gap-2">
                            <span className="text-surface-200">{l.amount} zł</span>
                            {l.paid
                              ? <span className="inline-flex items-center gap-1 text-xs text-emerald-400"><Check className="w-3 h-3" />{t('monthlyPayments.paidBadge')}</span>
                              : <span className="inline-flex items-center gap-1 text-xs text-surface-500"><X className="w-3 h-3" />{t('monthlyPayments.unpaidBadge')}</span>}
                          </span>
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
    </div>
  )
}
