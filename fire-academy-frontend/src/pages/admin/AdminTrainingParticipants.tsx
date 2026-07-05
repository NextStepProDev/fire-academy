import { useMemo, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Users, Check, X, Phone, Search, Mail, ChevronDown, ChevronRight } from 'lucide-react'
import { adminApi } from '../../api/admin'
import { Button } from '../../components/ui/Button'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { useToast } from '../../context/ToastContext'
import { adminVisibleMonths, currentMonth, formatMonth } from '../../utils/trainingSchedule'
import type { MonthlyTrainingLine } from '../../types'
import { AdminUserDetail } from './AdminUserDetail'
import clsx from 'clsx'

/** One participant on one training — the flat unit this bird's-eye view filters and counts. */
interface Row {
  userId: string
  firstName: string
  lastName: string
  email: string
  phone: string
  line: MonthlyTrainingLine
}

type Status = '' | 'paid' | 'unpaid' | 'overdue'

const selectClass =
  'px-3 py-2 bg-surface-800 border border-surface-700 rounded-lg text-surface-100 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500'

const money = (n: number) => `${Math.round(n * 100) / 100} zł`

/** Every current participant across all trainings, filterable by trainer / type / payment status. */
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
  const [selectedUserId, setSelectedUserId] = useState<string | null>(null)
  const [composerOpen, setComposerOpen] = useState(false)
  const [subject, setSubject] = useState('')
  const [message, setMessage] = useState('')

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['admin', 'training-payments', month],
    queryFn: () => adminApi.getMonthlyPayments(month),
    staleTime: 0,
    enabled: expanded,
  })

  const payMut = useMutation({
    mutationFn: ({ enrollmentId, paid }: { enrollmentId: string; paid: boolean }) =>
      adminApi.setTrainingPayment(enrollmentId, { month, paid }),
    onSuccess: (_r, v) => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'training-payments'] })
      queryClient.invalidateQueries({ queryKey: ['admin', 'training-roster'] })
      queryClient.invalidateQueries({ queryKey: ['admin', 'training-refunds'] })
      showToast(t(v.paid ? 'participants.paid' : 'participants.reverted'), 'success')
    },
    onError: (e: Error) => showToast(e.message, 'error'),
  })

  const emailMut = useMutation({
    mutationFn: (userIds: string[]) =>
      adminApi.sendUserEmail({ subject, message, audience: 'SELECTED', userIds }),
    onSuccess: () => {
      showToast(t('participants.remindSent'), 'success')
      setComposerOpen(false)
    },
    onError: (e: Error) => showToast(e.message, 'error'),
  })

  // Flatten everyone's monthly bill into one row per person × training.
  const rows: Row[] = useMemo(() => {
    const out: Row[] = []
    for (const u of data ?? []) {
      for (const line of u.trainings) {
        out.push({ userId: u.userId, firstName: u.firstName, lastName: u.lastName, email: u.email, phone: u.phone, line })
      }
    }
    return out
  }, [data])

  // Filter option lists, derived from the full (unfiltered) dataset so choices never disappear mid-filtering.
  const trainers = useMemo(() => {
    const map = new Map<string, string>()
    let hasNone = false
    for (const r of rows) {
      if (r.line.instructorId && r.line.instructorName) map.set(r.line.instructorId, r.line.instructorName)
      else hasNone = true
    }
    return { list: [...map.entries()].sort((a, b) => a[1].localeCompare(b[1], 'pl')), hasNone }
  }, [rows])

  const types = useMemo(() => {
    const map = new Map<string, string>()
    for (const r of rows) map.set(r.line.eventTypeId, r.line.trainingName)
    return [...map.entries()].sort((a, b) => a[1].localeCompare(b[1], 'pl'))
  }, [rows])

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase()
    return rows
      .filter(r => {
        if (trainer === 'none' ? r.line.instructorId != null : trainer && r.line.instructorId !== trainer) return false
        if (type && r.line.eventTypeId !== type) return false
        if (status === 'paid' && !r.line.paid) return false
        if (status === 'unpaid' && r.line.paid) return false
        if (status === 'overdue' && !r.line.overdue) return false
        if (q) {
          const hay = `${r.firstName} ${r.lastName} ${r.email}`.toLowerCase()
          if (!hay.includes(q)) return false
        }
        return true
      })
      .sort((a, b) =>
        `${a.lastName} ${a.firstName}`.localeCompare(`${b.lastName} ${b.firstName}`, 'pl') ||
        a.line.dayOfWeek - b.line.dayOfWeek ||
        a.line.startTime.localeCompare(b.line.startTime))
  }, [rows, search, trainer, type, status])

  const stats = useMemo(() => {
    const people = new Set<string>()
    let due = 0, paid = 0, overdue = 0
    for (const r of filtered) {
      people.add(r.userId)
      if (r.line.paid) paid += r.line.amount
      else due += r.line.amount
      if (r.line.overdue) overdue += r.line.amount
    }
    return { people: people.size, trainings: filtered.length, due, paid, overdue }
  }, [filtered])

  // Recipients of a payment reminder: distinct people among the filtered rows who are OVERDUE (their month's
  // first session already passed and it is still unpaid) — deliberately not everyone unpaid, so nobody gets
  // chased before their trainings have even started.
  const overdueUserIds = useMemo(
    () => [...new Set(filtered.filter(r => r.line.overdue).map(r => r.userId))],
    [filtered])

  const hasFilters = !!(search || trainer || type || status)
  const clearFilters = () => { setSearch(''); setTrainer(''); setType(''); setStatus('') }

  const openComposer = () => {
    if (!overdueUserIds.length) { showToast(t('participants.remindNobody'), 'error'); return }
    setSubject(t('participants.remindSubject', { month: formatMonth(month) }))
    setMessage(t('participants.remindMessage', { month: formatMonth(month) }))
    setComposerOpen(true)
  }

  const timeLabel = (l: MonthlyTrainingLine) => `${l.startTime.slice(0, 5)}${l.endTime ? `–${l.endTime.slice(0, 5)}` : ''}`

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
      ) : !rows.length ? (
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

          {/* List */}
          {!filtered.length ? (
            <p className="text-sm text-surface-500">{t('participants.noMatch')}</p>
          ) : (
            <div className={clsx('space-y-1.5 transition-opacity', isFetching && 'opacity-60')}>
              {filtered.map(r => {
                const l = r.line
                return (
                  <div key={`${r.userId}-${l.enrollmentId}`}
                    className={clsx('flex flex-wrap items-center gap-3 px-4 py-2.5 rounded-xl border bg-surface-900',
                      l.paid ? 'border-emerald-800/50' : l.overdue ? 'border-rose-900/40' : 'border-surface-800')}>
                    <button onClick={() => setSelectedUserId(r.userId)} className="min-w-0 flex-1 text-left group">
                      <span className="text-surface-100 font-medium group-hover:text-primary-300">{r.firstName} {r.lastName}</span>
                      <span className="block text-xs text-surface-500">
                        {t(`days.${l.dayOfWeek}`)} {timeLabel(l)} · {l.trainingName}
                        {l.instructorName && ` · ${l.instructorName}`}
                      </span>
                      <span className="block text-xs text-surface-500">
                        {r.phone && <span className="inline-flex items-center gap-1"><Phone className="w-3 h-3" />{r.phone} · </span>}{r.email}
                      </span>
                    </button>
                    <span className="text-surface-200 text-sm shrink-0">{money(l.amount)}</span>
                    <span className="shrink-0">
                      {l.paid
                        ? <span className="inline-flex items-center gap-1 text-xs text-emerald-400"><Check className="w-3.5 h-3.5" />{t('participants.paidBadge')}</span>
                        : l.overdue
                          ? <span className="inline-flex items-center gap-1 px-2 py-0.5 text-xs rounded-full bg-rose-500/10 text-rose-300">{t('participants.overdueBadge')}</span>
                          : <span className="inline-flex items-center gap-1 text-xs text-surface-500"><X className="w-3 h-3" />{t('participants.unpaidBadge')}</span>}
                    </span>
                    {l.paid ? (
                      <Button variant="ghost" size="sm" onClick={() => payMut.mutate({ enrollmentId: l.enrollmentId, paid: false })} disabled={payMut.isPending} className="text-surface-400 shrink-0">
                        {t('participants.markUnpaid')}
                      </Button>
                    ) : (
                      <Button variant="primary" size="sm" onClick={() => payMut.mutate({ enrollmentId: l.enrollmentId, paid: true })} disabled={payMut.isPending} className="shrink-0">
                        <Check className="w-3.5 h-3.5 mr-1" />{t('participants.markPaid')}
                      </Button>
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
