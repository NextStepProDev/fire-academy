import { useMemo, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Users, Check, X, Phone, Search, Mail, ChevronDown, ChevronRight, Pin, UserRound } from 'lucide-react'
import { adminApi } from '../../api/admin'
import { Button } from '../../components/ui/Button'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { useToast } from '../../context/ToastContext'
import { adminVisibleMonths, currentMonth, formatMonth } from '../../utils/trainingSchedule'
import { formatDate } from '../../utils/dates'
import type { MonthlyTrainingLine, UserMonthlyPayment } from '../../types'
import { AdminUserDetail } from './AdminUserDetail'
import clsx from 'clsx'

type Status = '' | 'paid' | 'unpaid' | 'overdue'

const selectClass =
  'px-3 py-2 bg-surface-800 border border-surface-700 rounded-lg text-surface-100 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500'

const money = (n: number) => `${Math.round(n * 100) / 100} zł`

const timeLabel = (l: MonthlyTrainingLine) =>
  `${l.startTime.slice(0, 5)}${l.endTime ? `–${l.endTime.slice(0, 5)}` : ''}`

/**
 * Single roster for the training section: everyone enrolled in a month, one card per person (never one row
 * per training). Filter by trainer / type / payment status; expand a person to see their trainings, settle
 * payments (whole month or a single training), and set the discretionary first-month start date.
 */
export function AdminTrainingParticipants() {
  const { t } = useTranslation('admin')
  const { showToast } = useToast()
  const queryClient = useQueryClient()
  const months = adminVisibleMonths()
  const [month, setMonth] = useState(currentMonth())

  // Collapsed by default; the roster is only fetched once the section is first opened (no idle DB hit).
  const [expanded, setExpanded] = useState(false)
  const [search, setSearch] = useState('')
  const [trainer, setTrainer] = useState('') // '' = all, 'none' = no trainer, otherwise instructorId
  const [type, setType] = useState('') // '' = all, otherwise eventTypeId
  const [status, setStatus] = useState<Status>('')
  const [openUsers, setOpenUsers] = useState<Set<string>>(new Set())
  const [selectedUserId, setSelectedUserId] = useState<string | null>(null)
  const [composerOpen, setComposerOpen] = useState(false)
  const [subject, setSubject] = useState('')
  const [message, setMessage] = useState('')

  const toggleUser = (id: string) =>
    setOpenUsers(prev => { const n = new Set(prev); if (n.has(id)) n.delete(id); else n.add(id); return n })

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['admin', 'training-payments', month],
    queryFn: () => adminApi.getMonthlyPayments(month),
    staleTime: 0,
    enabled: expanded,
  })

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'training-payments'] })
    queryClient.invalidateQueries({ queryKey: ['admin', 'training-roster'] })
    queryClient.invalidateQueries({ queryKey: ['admin', 'training-refunds'] })
  }

  // Whole-month settle for one person (covers all their trainings at once).
  const payMonthMut = useMutation({
    mutationFn: ({ userId, paid }: { userId: string; paid: boolean }) => adminApi.payUserMonth(userId, month, paid),
    onSuccess: (_r, v) => { invalidate(); showToast(t(v.paid ? 'participants.monthPaid' : 'participants.monthReverted'), 'success') },
    onError: (e: Error) => showToast(e.message, 'error'),
  })
  // Settle a single training (pins it — a whole-month revert then leaves it alone).
  const payLineMut = useMutation({
    mutationFn: ({ enrollmentId, paid }: { enrollmentId: string; paid: boolean }) => adminApi.setTrainingPayment(enrollmentId, { month, paid }),
    onSuccess: (_r, v) => { invalidate(); showToast(t(v.paid ? 'participants.paid' : 'participants.reverted'), 'success') },
    onError: (e: Error) => showToast(e.message, 'error'),
  })
  // First-month "count from day X". One id → per-training picker; many ids → the person's global start date.
  const startMut = useMutation({
    mutationFn: async ({ ids, startDate }: { ids: string[]; startDate: string | null }) => {
      await Promise.all(ids.map(id => adminApi.setTrainingStart(id, startDate)))
    },
    onSuccess: () => { invalidate(); showToast(t('participants.startUpdated'), 'success') },
    onError: (e: Error) => showToast(e.message, 'error'),
  })

  const emailMut = useMutation({
    mutationFn: (userIds: string[]) =>
      adminApi.sendUserEmail({ subject, message, audience: 'SELECTED', userIds }),
    onSuccess: () => { showToast(t('participants.remindSent'), 'success'); setComposerOpen(false) },
    onError: (e: Error) => showToast(e.message, 'error'),
  })

  const users = useMemo(() => data ?? [], [data])

  // Filter option lists, derived from the full (unfiltered) dataset so choices never disappear mid-filtering.
  const trainers = useMemo(() => {
    const map = new Map<string, string>()
    let hasNone = false
    for (const u of users) for (const l of u.trainings) {
      if (l.instructorId && l.instructorName) map.set(l.instructorId, l.instructorName)
      else hasNone = true
    }
    return { list: [...map.entries()].sort((a, b) => a[1].localeCompare(b[1], 'pl')), hasNone }
  }, [users])

  const types = useMemo(() => {
    const map = new Map<string, string>()
    for (const u of users) for (const l of u.trainings) map.set(l.eventTypeId, l.trainingName)
    return [...map.entries()].sort((a, b) => a[1].localeCompare(b[1], 'pl'))
  }, [users])

  const q = search.trim().toLowerCase()
  // Facets match a single training; search matches the person. A person is shown when they pass the search
  // AND have at least one training matching the facets — but we always render ALL of their trainings, so the
  // person's total never disagrees with the lines under it.
  const facet = (l: MonthlyTrainingLine) => {
    if (trainer === 'none' ? l.instructorId != null : trainer && l.instructorId !== trainer) return false
    if (type && l.eventTypeId !== type) return false
    if (status === 'paid' && !l.paid) return false
    if (status === 'unpaid' && l.paid) return false
    if (status === 'overdue' && !l.overdue) return false
    return true
  }
  const searchHit = (u: UserMonthlyPayment) => !q || `${u.firstName} ${u.lastName} ${u.email}`.toLowerCase().includes(q)

  const people = useMemo(
    () => users
      .filter(u => searchHit(u) && u.trainings.some(facet))
      .sort((a, b) => `${a.lastName} ${a.firstName}`.localeCompare(`${b.lastName} ${b.firstName}`, 'pl')),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [users, q, trainer, type, status])

  // Counters describe the filtered slice (matching trainings only), so they answer "how much of what I filtered
  // for is still owed" — independent of the fact that each shown person also lists their non-matching trainings.
  const stats = useMemo(() => {
    const ppl = new Set<string>()
    let due = 0, paid = 0, overdue = 0, count = 0
    for (const u of users) {
      if (!searchHit(u)) continue
      for (const l of u.trainings) {
        if (!facet(l)) continue
        ppl.add(u.userId); count++
        if (l.paid) paid += l.amount; else due += l.amount
        if (l.overdue) overdue += l.amount
      }
    }
    return { people: ppl.size, trainings: count, due, paid, overdue }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [users, q, trainer, type, status])

  // Reminder recipients: distinct people with an OVERDUE matching training (month's first session already passed
  // and still unpaid) — deliberately not everyone unpaid, so nobody is chased before their trainings have started.
  const overdueUserIds = useMemo(() => {
    const s = new Set<string>()
    for (const u of users) {
      if (!searchHit(u)) continue
      for (const l of u.trainings) if (facet(l) && l.overdue) { s.add(u.userId); break }
    }
    return [...s]
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [users, q, trainer, type, status])

  const hasFilters = !!(search || trainer || type || status)
  const clearFilters = () => { setSearch(''); setTrainer(''); setType(''); setStatus('') }

  const openComposer = () => {
    if (!overdueUserIds.length) { showToast(t('participants.remindNobody'), 'error'); return }
    setSubject(t('participants.remindSubject', { month: formatMonth(month) }))
    setMessage(t('participants.remindMessage', { month: formatMonth(month) }))
    setComposerOpen(true)
  }

  // Last day of the viewed month (ISO), to cap the "count from" date pickers to that month.
  const monthLastDay = `${month}-${String(new Date(Number(month.slice(0, 4)), Number(month.slice(5, 7)), 0).getDate()).padStart(2, '0')}`

  if (selectedUserId) {
    return <AdminUserDetail userId={selectedUserId} onBack={() => setSelectedUserId(null)} />
  }

  return (
    <div>
      <button onClick={() => setExpanded(v => !v)} className="flex items-center gap-2 mb-1 text-left w-full group">
        {expanded ? <ChevronDown className="w-4 h-4 text-surface-400 shrink-0" /> : <ChevronRight className="w-4 h-4 text-surface-400 shrink-0" />}
        <Users className="w-5 h-5 text-primary-400 shrink-0" />
        <h2 className="text-xl font-semibold text-surface-100 group-hover:text-primary-300">{t('participants.title')}</h2>
      </button>
      <p className="text-sm text-surface-500 mb-4 pl-6">{t('participants.hint')}</p>

      {expanded && (<>
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
        <p className="text-sm text-surface-500">{t('participants.empty')}</p>
      ) : (
        <>
          {/* Filters */}
          <div className="flex flex-wrap gap-2 mb-3">
            <div className="relative flex-1 min-w-[12rem]">
              <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 w-4 h-4 text-surface-500" />
              <input
                type="search"
                value={search}
                onChange={e => setSearch(e.target.value)}
                placeholder={t('participants.search')}
                className={clsx(selectClass, 'w-full pl-8')}
              />
            </div>
            <select value={trainer} onChange={e => setTrainer(e.target.value)} className={selectClass}>
              <option value="">{t('participants.allTrainers')}</option>
              {trainers.list.map(([id, name]) => <option key={id} value={id}>{name}</option>)}
              {trainers.hasNone && <option value="none">{t('participants.noTrainer')}</option>}
            </select>
            <select value={type} onChange={e => setType(e.target.value)} className={selectClass}>
              <option value="">{t('participants.allTypes')}</option>
              {types.map(([id, name]) => <option key={id} value={id}>{name}</option>)}
            </select>
            <select value={status} onChange={e => setStatus(e.target.value as Status)} className={selectClass}>
              <option value="">{t('participants.statusAll')}</option>
              <option value="paid">{t('participants.statusPaid')}</option>
              <option value="unpaid">{t('participants.statusUnpaid')}</option>
              <option value="overdue">{t('participants.statusOverdue')}</option>
            </select>
            {hasFilters && (
              <Button variant="ghost" size="sm" onClick={clearFilters} className="text-surface-400">
                {t('participants.clearFilters')}
              </Button>
            )}
          </div>

          {/* Counters */}
          <div className="grid grid-cols-2 sm:grid-cols-5 gap-2 mb-3">
            <Stat label={t('participants.statPeople')} value={String(stats.people)} />
            <Stat label={t('participants.statTrainings')} value={String(stats.trainings)} />
            <Stat label={t('participants.statDue')} value={money(stats.due)} tone="primary" />
            <Stat label={t('participants.statPaid')} value={money(stats.paid)} tone="emerald" />
            <Stat label={t('participants.statOverdue')} value={money(stats.overdue)} tone={stats.overdue > 0 ? 'rose' : undefined} />
          </div>

          {/* Reminder — overdue people only */}
          <div className="mb-3">
            <Button variant="secondary" size="sm" onClick={openComposer} disabled={!overdueUserIds.length}>
              <Mail className="w-3.5 h-3.5 mr-1" />
              {t('participants.remindCount', { count: overdueUserIds.length })}
            </Button>
            <p className="mt-1 text-xs text-surface-500 max-w-2xl">{t('participants.remindWho')}</p>
            {composerOpen && (
              <div className="mt-2 p-3 rounded-xl border border-surface-800 bg-surface-900 space-y-2">
                <label className="block text-xs text-surface-400">
                  {t('participants.remindSubjectLabel')}
                  <input value={subject} onChange={e => setSubject(e.target.value)} className={clsx(selectClass, 'w-full mt-1')} />
                </label>
                <label className="block text-xs text-surface-400">
                  {t('participants.remindMessageLabel')}
                  <textarea value={message} onChange={e => setMessage(e.target.value)} rows={4} className={clsx(selectClass, 'w-full mt-1')} />
                </label>
                <div className="flex gap-2">
                  <Button variant="primary" size="sm"
                    onClick={() => emailMut.mutate(overdueUserIds)}
                    disabled={emailMut.isPending || !subject.trim() || !message.trim()}>
                    {t('participants.remindSend', { count: overdueUserIds.length })}
                  </Button>
                  <Button variant="ghost" size="sm" onClick={() => setComposerOpen(false)} className="text-surface-400">
                    {t('participants.cancel')}
                  </Button>
                </div>
              </div>
            )}
          </div>

          {/* People */}
          {!people.length ? (
            <p className="text-sm text-surface-500">{t('participants.noMatch')}</p>
          ) : (
            <div className={clsx('space-y-2 transition-opacity', isFetching && 'opacity-60')}>
              {people.map(u => {
                const open = openUsers.has(u.userId)
                const paidAmount = u.trainings.filter(l => l.paid).reduce((s, l) => s + l.amount, 0)
                const remaining = Math.max(0, u.totalAmount - paidAmount)
                const partiallyPaid = !u.allPaid && paidAmount > 0
                const hasOverdue = u.trainings.some(l => l.overdue)
                // Whole-month revert only clears trainings paid via the batch action, never individually-pinned
                // ones, so hide it when there is nothing it would actually undo.
                const hasRevertible = u.trainings.some(l => l.paid && !l.pinned)
                // First-month, still-unpaid trainings — the only ones whose start date can be set this month.
                const firstUnpaid = u.trainings.filter(l => l.startMonth === month && !l.paid)
                const startIds = firstUnpaid.map(l => l.enrollmentId)
                const starts = new Set(firstUnpaid.map(l => l.billableFrom ?? ''))
                const commonStart = starts.size === 1 ? [...starts][0] : ''
                return (
                  <div key={u.userId} className={clsx('rounded-xl border bg-surface-900 overflow-hidden',
                    u.allPaid ? 'border-emerald-800/50' : hasOverdue ? 'border-rose-900/40' : 'border-surface-800')}>
                    <div className="flex flex-wrap items-center gap-3 px-4 py-3">
                      <button onClick={() => toggleUser(u.userId)} className="flex items-center gap-2 min-w-0 flex-1 text-left group">
                        {open ? <ChevronDown className="w-4 h-4 text-surface-400 shrink-0" /> : <ChevronRight className="w-4 h-4 text-surface-400 shrink-0" />}
                        <span className="min-w-0">
                          <span className="text-surface-100 font-medium group-hover:text-primary-300">{u.firstName} {u.lastName}</span>
                          <span className={clsx('ml-2 text-sm font-semibold', u.allPaid ? 'text-emerald-400' : 'text-primary-400')}>{money(u.totalAmount)}</span>
                          <span className="block text-xs text-surface-500">
                            {t('participants.trainingsCount', { count: u.trainings.length })}
                            {u.creditBalance > 0 && ` · ${t('participants.credit', { amount: u.creditBalance })}`}
                            {' · '}{u.phone && <span className="inline-flex items-center gap-1"><Phone className="w-3 h-3" />{u.phone} · </span>}{u.email}
                          </span>
                          {partiallyPaid && (
                            <span className="block text-xs text-primary-300 mt-0.5">
                              {t('participants.partialSummary', { paid: paidAmount, remaining })}
                            </span>
                          )}
                        </span>
                      </button>

                      <button
                        onClick={() => setSelectedUserId(u.userId)}
                        title={t('participants.openProfile')}
                        aria-label={t('participants.openProfile')}
                        className="shrink-0 text-surface-400 hover:text-primary-300"
                      >
                        <UserRound className="w-4 h-4" />
                      </button>

                      {/* Global first-month start date for this person — applies to all their first, unpaid trainings. */}
                      {startIds.length > 0 && (
                        <label className="flex items-center gap-1.5 text-xs text-surface-400 shrink-0" title={t('participants.startAllHint')}>
                          {t('participants.startAll')}
                          <input
                            type="date"
                            min={`${month}-01`}
                            max={monthLastDay}
                            value={commonStart}
                            onChange={e => startMut.mutate({ ids: startIds, startDate: e.target.value || null })}
                            disabled={startMut.isPending}
                            className="px-1.5 py-0.5 bg-surface-800 border border-surface-700 rounded text-surface-200"
                          />
                        </label>
                      )}

                      {u.allPaid ? (
                        <div className="flex items-center gap-2 shrink-0">
                          <span className="inline-flex items-center gap-1 px-2 py-0.5 text-xs rounded-full bg-emerald-900/30 text-emerald-400"><Check className="w-3.5 h-3.5" />{t('participants.paidBadge')}</span>
                          {u.paidAt && <span className="text-xs text-surface-500">{t('participants.paidOn', { date: formatDate(u.paidAt) })}</span>}
                          {hasRevertible && (
                            <Button variant="ghost" size="sm" onClick={() => payMonthMut.mutate({ userId: u.userId, paid: false })} disabled={payMonthMut.isPending} className="text-surface-400">
                              {t('participants.revertMonth')}
                            </Button>
                          )}
                        </div>
                      ) : (
                        <Button variant="primary" size="sm" onClick={() => payMonthMut.mutate({ userId: u.userId, paid: true })} disabled={payMonthMut.isPending} className="shrink-0">
                          <Check className="w-3.5 h-3.5 mr-1" />{t('participants.payMonth')}
                        </Button>
                      )}
                    </div>

                    {open && (
                      <div className="border-t border-surface-800 px-4 py-2 space-y-1">
                        {u.trainings.map(l => (
                          <div key={l.enrollmentId} className="py-1">
                            <div className="flex flex-wrap items-center justify-between gap-2 text-sm">
                              <span className="text-surface-300">
                                {t(`days.${l.dayOfWeek}`)} {timeLabel(l)} · {l.trainingName}
                                {l.instructorName && ` · ${l.instructorName}`}
                              </span>
                              <span className="flex items-center gap-2">
                                <span className="text-surface-200">{money(l.amount)}</span>
                                {l.paid
                                  ? <span className="inline-flex items-center gap-1 text-xs text-emerald-400" title={l.pinned ? t('participants.pinnedHint') : undefined}><Check className="w-3 h-3" />{t('participants.paidBadge')}{l.pinned && <Pin className="w-3 h-3" />}</span>
                                  : l.overdue
                                    ? <span className="inline-flex items-center gap-1 px-2 py-0.5 text-xs rounded-full bg-rose-500/10 text-rose-300" title={t('participants.overdueHint')}>{t('participants.overdueBadge')}</span>
                                    : <span className="inline-flex items-center gap-1 text-xs text-surface-500"><X className="w-3 h-3" />{t('participants.unpaidBadge')}</span>}
                                {l.paid ? (
                                  <Button variant="ghost" size="sm" onClick={() => payLineMut.mutate({ enrollmentId: l.enrollmentId, paid: false })} disabled={payLineMut.isPending} className="text-surface-400">
                                    {t('participants.markUnpaid')}
                                  </Button>
                                ) : (
                                  <Button variant="primary" size="sm" onClick={() => payLineMut.mutate({ enrollmentId: l.enrollmentId, paid: true })} disabled={payLineMut.isPending}>
                                    <Check className="w-3.5 h-3.5 mr-1" />{t('participants.markPaid')}
                                  </Button>
                                )}
                              </span>
                            </div>
                            {/* First-month "count from day X" — set the real start here so the total updates before paying. */}
                            {!l.paid && l.startMonth === month && (
                              <div className="flex items-center gap-1.5 mt-1 text-xs text-surface-400">
                                {t('participants.billFrom')}
                                <input
                                  type="date"
                                  min={`${month}-01`}
                                  max={monthLastDay}
                                  value={l.billableFrom ?? ''}
                                  onChange={e => startMut.mutate({ ids: [l.enrollmentId], startDate: e.target.value || null })}
                                  disabled={startMut.isPending}
                                  className="px-1.5 py-0.5 bg-surface-800 border border-surface-700 rounded text-surface-200"
                                  title={t('participants.billFromHint')}
                                />
                              </div>
                            )}
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )
              })}
            </div>
          )}
        </>
      )}
      </>)}
    </div>
  )
}

function Stat({ label, value, tone }: { label: string; value: string; tone?: 'primary' | 'emerald' | 'rose' }) {
  return (
    <div className="px-3 py-2 rounded-xl border border-surface-800 bg-surface-900">
      <div className="text-xs text-surface-500">{label}</div>
      <div className={clsx('text-lg font-semibold',
        tone === 'primary' ? 'text-primary-400' : tone === 'emerald' ? 'text-emerald-400' : tone === 'rose' ? 'text-rose-300' : 'text-surface-100')}>
        {value}
      </div>
    </div>
  )
}
