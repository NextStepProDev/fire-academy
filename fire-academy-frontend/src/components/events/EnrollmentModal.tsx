import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Modal } from '../ui/Modal'
import { Button } from '../ui/Button'
import { publicApi } from '../../api/public'
import { CheckCircle } from 'lucide-react'

interface EnrollmentModalProps {
  isOpen: boolean
  onClose: () => void
  eventId: string | null
  eventName: string
}

export function EnrollmentModal({ isOpen, onClose, eventId, eventName }: EnrollmentModalProps) {
  const { t } = useTranslation('events')
  const [form, setForm] = useState({ firstName: '', lastName: '', email: '', phone: '' })
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [success, setSuccess] = useState(false)

  const handleClose = () => {
    setForm({ firstName: '', lastName: '', email: '', phone: '' })
    setError(null)
    setSuccess(false)
    onClose()
  }

  const validate = (): string | null => {
    if (!form.firstName.trim()) return t('enroll.firstNameRequired')
    if (!form.lastName.trim()) return t('enroll.lastNameRequired')
    if (!form.email.trim()) return t('enroll.emailRequired')
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) return t('enroll.emailInvalid')
    if (!form.phone.trim()) return t('enroll.phoneRequired')
    const digits = form.phone.replace(/\s/g, '')
    if (!/^(\d{9}|\+\d{2}\d{9})$/.test(digits)) return t('enroll.phoneInvalid')
    return null
  }

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    const validationError = validate()
    if (validationError) { setError(validationError); return }
    if (!eventId) return

    setLoading(true)
    try {
      await publicApi.enroll(eventId, { ...form, phone: form.phone.replace(/\s/g, '') })
      setSuccess(true)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setLoading(false)
    }
  }

  if (!isOpen) return null

  return (
    <Modal isOpen={isOpen} onClose={handleClose} title={success ? '' : `${t('enroll.title')} — ${eventName}`}>
      {success ? (
        <div className="text-center py-4">
          <CheckCircle className="w-12 h-12 text-green-400 mx-auto mb-3" />
          <p className="text-surface-100 font-medium">{t('enroll.success')}</p>
        </div>
      ) : (
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-surface-300 mb-1">{t('enroll.firstName')}</label>
              <input
                type="text"
                required
                value={form.firstName}
                onChange={e => setForm(f => ({ ...f, firstName: e.target.value }))}
                className="w-full px-3 py-2 bg-surface-800 border border-surface-700 rounded-lg text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-surface-300 mb-1">{t('enroll.lastName')}</label>
              <input
                type="text"
                required
                value={form.lastName}
                onChange={e => setForm(f => ({ ...f, lastName: e.target.value }))}
                className="w-full px-3 py-2 bg-surface-800 border border-surface-700 rounded-lg text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
              />
            </div>
          </div>
          <div>
            <label className="block text-sm font-medium text-surface-300 mb-1">{t('enroll.email')}</label>
            <input
              type="email"
              required
              value={form.email}
              onChange={e => setForm(f => ({ ...f, email: e.target.value }))}
              className="w-full px-3 py-2 bg-surface-800 border border-surface-700 rounded-lg text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-surface-300 mb-1">{t('enroll.phone')}</label>
            <input
              type="tel"
              required
              value={form.phone}
              onChange={e => setForm(f => ({ ...f, phone: e.target.value }))}
              className="w-full px-3 py-2 bg-surface-800 border border-surface-700 rounded-lg text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
            />
          </div>
          {error && <p className="text-sm text-rose-400/80">{error}</p>}
          <Button type="submit" variant="primary" className="w-full" loading={loading}>
            {t('enroll.submit')}
          </Button>
        </form>
      )}
    </Modal>
  )
}
