import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Modal } from '../ui/Modal'
import { Button } from '../ui/Button'
import { ProfileCompletionForm } from '../profile/ProfileCompletionForm'
import { needsProfileCompletion } from '../../utils/profileCompletion'
import { userApi } from '../../api/client'
import { useAuth } from '../../context/AuthContext'
import { CheckCircle, UserPlus } from 'lucide-react'

interface EnrollmentModalProps {
  isOpen: boolean
  onClose: () => void
  eventId: string | null
  eventName: string
  onEnrolled?: () => void
}

const inputBase = 'w-full px-3 py-2 bg-surface-800 border rounded-lg text-surface-100 focus:outline-none focus:ring-2'
const inputOk = `${inputBase} border-surface-700 focus:ring-primary-500`

export function EnrollmentModal({ isOpen, onClose, eventId, eventName, onEnrolled }: EnrollmentModalProps) {
  const { t } = useTranslation('events')
  const { user } = useAuth()
  const [note, setNote] = useState('')
  const [serverError, setServerError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [success, setSuccess] = useState(false)

  const handleClose = () => {
    setNote('')
    setServerError(null)
    setSuccess(false)
    onClose()
  }

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setServerError(null)
    if (!eventId) return
    setLoading(true)
    try {
      await userApi.enroll(eventId, note)
      setSuccess(true)
      onEnrolled?.()
    } catch (err) {
      setServerError(err instanceof Error ? err.message : String(err))
    } finally {
      setLoading(false)
    }
  }

  if (!isOpen || !user) return null

  // Brak wymaganych danych LUB niezaakceptowana polityka prywatności → najpierw domknij konto.
  const incompleteProfile = needsProfileCompletion(user)

  return (
    <Modal isOpen={isOpen} onClose={handleClose} title={success ? '' : `${t('enroll.title')} — ${eventName}`}>
      {success ? (
        <div className="text-center py-4">
          <CheckCircle className="w-12 h-12 text-green-400 mx-auto mb-3" />
          <p className="text-surface-100 font-medium">{t('enroll.success')}</p>
        </div>
      ) : incompleteProfile ? (
        <div className="space-y-4 py-2">
          <div className="flex items-start gap-3">
            <UserPlus className="w-6 h-6 text-primary-400 shrink-0 mt-0.5" />
            <div>
              <p className="text-surface-100 font-medium">{t('enroll.profileIncompleteTitle')}</p>
              <p className="text-sm text-surface-400 mt-1">{t('enroll.profileIncompleteText')}</p>
            </div>
          </div>
          <ProfileCompletionForm submitLabel={t('enroll.profileIncompleteCta')} />
        </div>
      ) : (
        <form onSubmit={handleSubmit} className="space-y-4" noValidate>
          <p className="text-sm text-surface-400">{t('enroll.intro')}</p>
          <dl className="bg-surface-800/60 rounded-lg p-4 grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-3">
            <div>
              <dt className="text-xs uppercase tracking-wide text-surface-500">{t('enroll.name')}</dt>
              <dd className="text-surface-100 mt-0.5">{user.firstName} {user.lastName}</dd>
            </div>
            <div>
              <dt className="text-xs uppercase tracking-wide text-surface-500">{t('enroll.phone')}</dt>
              <dd className="text-surface-100 mt-0.5">{user.phone}</dd>
            </div>
            <div className="sm:col-span-2">
              <dt className="text-xs uppercase tracking-wide text-surface-500">{t('enroll.email')}</dt>
              <dd className="text-surface-100 mt-0.5 break-all">{user.email}</dd>
            </div>
          </dl>
          <div>
            <label className="block text-sm font-medium text-surface-300 mb-1">
              {t('enroll.note')} <span className="text-surface-500 font-normal">({t('enroll.optional')})</span>
            </label>
            <textarea
              value={note}
              onChange={e => setNote(e.target.value)}
              rows={3}
              maxLength={2000}
              className={`${inputOk} resize-none`}
            />
          </div>
          {serverError && <p className="text-sm text-rose-400/80">{serverError}</p>}
          <Button type="submit" variant="primary" className="w-full" loading={loading}>
            {t('enroll.submit')}
          </Button>
        </form>
      )}
    </Modal>
  )
}
