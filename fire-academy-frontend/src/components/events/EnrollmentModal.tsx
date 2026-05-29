import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
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

type FieldErrors = Partial<Record<'firstName' | 'lastName' | 'email' | 'phone' | 'privacy', string>>

const inputBase = 'w-full px-3 py-2 bg-surface-800 border rounded-lg text-surface-100 focus:outline-none focus:ring-2'
const inputOk = `${inputBase} border-surface-700 focus:ring-primary-500`
const inputErr = `${inputBase} border-rose-500/60 focus:ring-rose-500`

export function EnrollmentModal({ isOpen, onClose, eventId, eventName }: EnrollmentModalProps) {
  const { t } = useTranslation('events')
  const [form, setForm] = useState({ firstName: '', lastName: '', email: '', phone: '', note: '' })
  const [privacyAccepted, setPrivacyAccepted] = useState(false)
  const [errors, setErrors] = useState<FieldErrors>({})
  const [touched, setTouched] = useState<Set<string>>(new Set())
  const [serverError, setServerError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [success, setSuccess] = useState(false)

  const handleClose = () => {
    setForm({ firstName: '', lastName: '', email: '', phone: '', note: '' })
    setPrivacyAccepted(false)
    setErrors({})
    setTouched(new Set())
    setServerError(null)
    setSuccess(false)
    onClose()
  }

  const validate = (fields = form, checkPrivacy = false): FieldErrors => {
    const errs: FieldErrors = {}
    if (!fields.firstName.trim()) errs.firstName = t('enroll.firstNameRequired')
    else if (fields.firstName.trim().length < 3) errs.firstName = t('enroll.nameMinLength')
    if (!fields.lastName.trim()) errs.lastName = t('enroll.lastNameRequired')
    else if (fields.lastName.trim().length < 3) errs.lastName = t('enroll.nameMinLength')
    if (!fields.email.trim()) errs.email = t('enroll.emailRequired')
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(fields.email.trim())) errs.email = t('enroll.emailInvalid')
    if (!fields.phone.trim()) errs.phone = t('enroll.phoneRequired')
    else {
      const digits = fields.phone.replace(/\s/g, '')
      if (!/^(\d{9}|\+\d{2}\d{9})$/.test(digits)) errs.phone = t('enroll.phoneInvalid')
    }
    if (checkPrivacy && !privacyAccepted) errs.privacy = t('enroll.privacyRequired')
    return errs
  }

  const handleBlur = (field: string) => {
    setTouched(prev => new Set(prev).add(field))
    setErrors(validate())
  }

  const fieldError = (field: keyof FieldErrors) =>
    touched.has(field) ? errors[field] : undefined

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setServerError(null)

    const allFields = new Set(['firstName', 'lastName', 'email', 'phone', 'privacy'])
    setTouched(allFields)

    const errs = validate(form, true)
    setErrors(errs)
    if (Object.keys(errs).length > 0) return
    if (!eventId) return

    setLoading(true)
    try {
      await publicApi.enroll(eventId, {
        ...form,
        phone: form.phone.replace(/\s/g, ''),
        note: form.note.trim() || undefined,
      })
      setSuccess(true)
    } catch (err) {
      setServerError(err instanceof Error ? err.message : String(err))
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
        <form onSubmit={handleSubmit} className="space-y-4" noValidate>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-surface-300 mb-1">{t('enroll.firstName')}</label>
              <input
                type="text"
                value={form.firstName}
                onChange={e => setForm(f => ({ ...f, firstName: e.target.value }))}
                onBlur={() => handleBlur('firstName')}
                className={fieldError('firstName') ? inputErr : inputOk}
              />
              {fieldError('firstName') && <p className="text-xs text-rose-400 mt-1">{fieldError('firstName')}</p>}
            </div>
            <div>
              <label className="block text-sm font-medium text-surface-300 mb-1">{t('enroll.lastName')}</label>
              <input
                type="text"
                value={form.lastName}
                onChange={e => setForm(f => ({ ...f, lastName: e.target.value }))}
                onBlur={() => handleBlur('lastName')}
                className={fieldError('lastName') ? inputErr : inputOk}
              />
              {fieldError('lastName') && <p className="text-xs text-rose-400 mt-1">{fieldError('lastName')}</p>}
            </div>
          </div>
          <div>
            <label className="block text-sm font-medium text-surface-300 mb-1">{t('enroll.email')}</label>
            <input
              type="email"
              value={form.email}
              onChange={e => setForm(f => ({ ...f, email: e.target.value }))}
              onBlur={() => handleBlur('email')}
              className={fieldError('email') ? inputErr : inputOk}
              placeholder="jan@przykład.pl"
            />
            {fieldError('email') && <p className="text-xs text-rose-400 mt-1">{fieldError('email')}</p>}
          </div>
          <div>
            <label className="block text-sm font-medium text-surface-300 mb-1">{t('enroll.phone')}</label>
            <input
              type="tel"
              value={form.phone}
              onChange={e => setForm(f => ({ ...f, phone: e.target.value }))}
              onBlur={() => handleBlur('phone')}
              className={fieldError('phone') ? inputErr : inputOk}
              placeholder="123 456 789"
            />
            {fieldError('phone') && <p className="text-xs text-rose-400 mt-1">{fieldError('phone')}</p>}
          </div>
          <div>
            <label className="block text-sm font-medium text-surface-300 mb-1">
              {t('enroll.note')} <span className="text-surface-500 font-normal">({t('enroll.optional')})</span>
            </label>
            <textarea
              value={form.note}
              onChange={e => setForm(f => ({ ...f, note: e.target.value }))}
              rows={3}
              className={`${inputOk} resize-none`}
            />
          </div>
          <div>
            <label className="flex items-start gap-2 cursor-pointer">
              <input
                type="checkbox"
                checked={privacyAccepted}
                onChange={e => {
                  setPrivacyAccepted(e.target.checked)
                  if (e.target.checked) setErrors(prev => { const { privacy: _, ...rest } = prev; return rest })
                }}
                className="mt-0.5 w-4 h-4 rounded border-surface-600 bg-surface-800 text-primary-500 focus:ring-primary-500 shrink-0"
              />
              <span className="text-sm text-surface-400">
                {t('enroll.privacyLabel')}{' '}
                <Link to="/polityka-prywatnosci" target="_blank" className="text-primary-400 hover:text-primary-300 underline transition-colors">
                  {t('enroll.privacyLink')}
                </Link>
              </span>
            </label>
            {fieldError('privacy') && <p className="text-xs text-rose-400 mt-1 ml-6">{fieldError('privacy')}</p>}
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
