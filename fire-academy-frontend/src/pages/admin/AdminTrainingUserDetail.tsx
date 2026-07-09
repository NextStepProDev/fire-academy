import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { ArrowLeft, Mail, Phone } from 'lucide-react'
import { adminApi } from '../../api/admin'
import { Avatar } from '../../components/ui/Avatar'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { formatDate } from '../../utils/dates'
import { TrainingHistoryPanel } from './TrainingHistoryPanel'

/**
 * A client's training-focused card, opened from the "Uczestnicy" section — no event sign-up tools (those live in the
 * "Users" tab). The same training body is reused there as a collapsible section, so any client is reachable both
 * from the monthly roster and from the general user list.
 */
export function AdminTrainingUserDetail({ userId, onBack }: { userId: string; onBack: () => void }) {
  const { t } = useTranslation('admin')
  const { data: u, isLoading } = useQuery({
    queryKey: ['admin', 'training-user-history', userId],
    queryFn: () => adminApi.getTrainingUserHistory(userId),
  })

  if (isLoading || !u) return <LoadingSpinner />

  return (
    <div>
      <button onClick={onBack} className="inline-flex items-center gap-2 text-surface-400 hover:text-primary-300 mb-4">
        <ArrowLeft className="w-4 h-4" /> {t('trainingUserDetail.back')}
      </button>

      <div className="flex flex-wrap items-center gap-4 mb-8">
        <Avatar name={`${u.firstName} ${u.lastName}`} className="w-16 h-16" textClassName="text-xl" />
        <div className="min-w-0">
          <h2 className="text-xl font-semibold text-surface-100">{u.firstName} {u.lastName}</h2>
          <p className="text-surface-400 text-sm flex flex-wrap items-center gap-x-3 gap-y-1 mt-0.5">
            <span className="inline-flex items-center gap-1"><Mail className="w-4 h-4" />{u.email}</span>
            {u.phone && <span className="inline-flex items-center gap-1"><Phone className="w-4 h-4" />{u.phone}</span>}
          </p>
          <p className="text-xs text-surface-500 mt-0.5">{t('trainingUserDetail.joined', { date: formatDate(u.joinedAt) })}</p>
        </div>
      </div>

      <TrainingHistoryPanel key={userId} history={u} />
    </div>
  )
}
