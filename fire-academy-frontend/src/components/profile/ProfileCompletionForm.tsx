import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../../context/AuthContext'
import { authApi } from '../../api/client'
import { Button } from '../ui/Button'
import { getMissingProfileFields, type ProfileField } from '../../utils/profileCompletion'

const inputClass =
  'w-full px-3 py-2 bg-surface-800 border border-surface-700 rounded-lg text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500'

const FIELD_INPUT_TYPE: Record<ProfileField, string> = { firstName: 'text', lastName: 'text', phone: 'tel' }
const FIELD_AUTOCOMPLETE: Record<ProfileField, string> = { firstName: 'given-name', lastName: 'family-name', phone: 'tel' }

interface ProfileCompletionFormProps {
  /** Etykieta przycisku zatwierdzenia (domyślnie „Zapisz i kontynuuj"). */
  submitLabel?: string
}

/**
 * Formularz domknięcia konta po rejestracji/logowaniu: brakujące wymagane pola profilu
 * (imię, nazwisko, telefon) oraz — dla kont Google bez zgody — obowiązkowa akceptacja
 * polityki prywatności (RODO) i opcjonalna zgoda marketingowa. Renderuje tylko to, czego
 * brakuje, zapisuje na koncie i odświeża usera w `AuthContext`; komponent nadrzędny reaguje
 * na zmianę usera (braki znikają), więc nie potrzebuje callbacku.
 */
export function ProfileCompletionForm({ submitLabel }: ProfileCompletionFormProps) {
  const { t } = useTranslation(['settings', 'auth'])
  const { user, refreshUser } = useAuth()
  const missing = getMissingProfileFields(user)
  const needPrivacy = !!user && !user.privacyAccepted
  const [values, setValues] = useState<Record<ProfileField, string>>({ firstName: '', lastName: '', phone: '' })
  const [acceptedPrivacy, setAcceptedPrivacy] = useState(false)
  const [acceptedMarketing, setAcceptedMarketing] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  if (!user || (missing.length === 0 && !needPrivacy)) return null

  const setField = (field: ProfileField, value: string) =>
    setValues((prev) => ({ ...prev, [field]: value }))

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)

    for (const field of missing) {
      const value = values[field].trim()
      if (!value) {
        setError(t('completion.errors.required'))
        return
      }
      if ((field === 'firstName' || field === 'lastName') && value.length < 3) {
        setError(t('completion.errors.nameTooShort'))
        return
      }
    }

    if (needPrivacy && !acceptedPrivacy) {
      setError(t('register.privacyRequired', { ns: 'auth' }))
      return
    }

    setLoading(true)
    try {
      if (missing.length > 0) {
        // Pola spoza listy braków zachowują dotychczasowe wartości z konta.
        await authApi.updateProfile(
          missing.includes('firstName') ? values.firstName.trim() : user.firstName,
          missing.includes('lastName') ? values.lastName.trim() : user.lastName,
          missing.includes('phone') ? values.phone.trim() : (user.phone ?? ''),
        )
      }
      if (needPrivacy) {
        await authApi.submitConsents(true, acceptedMarketing)
      }
      await refreshUser()
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setLoading(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4" noValidate>
      {missing.map((field, index) => (
        <div key={field}>
          <label className="block text-sm font-medium text-surface-300 mb-1">{t(`profile.${field}`)}</label>
          <input
            type={FIELD_INPUT_TYPE[field]}
            autoComplete={FIELD_AUTOCOMPLETE[field]}
            value={values[field]}
            onChange={(e) => setField(field, e.target.value)}
            className={inputClass}
            autoFocus={index === 0}
          />
        </div>
      ))}

      {needPrivacy && (
        <div className="space-y-3 pt-1">
          <div className="flex items-start gap-2">
            <input
              id="completionPrivacy"
              type="checkbox"
              checked={acceptedPrivacy}
              onChange={(e) => { setAcceptedPrivacy(e.target.checked); setError(null) }}
              className="mt-0.5 h-4 w-4 rounded border-surface-600 bg-surface-800 text-primary-500 focus:ring-2 focus:ring-primary-500"
            />
            <label htmlFor="completionPrivacy" className="text-sm text-surface-300">
              {t('register.acceptPrivacyPrefix', { ns: 'auth' })}{' '}
              <Link to="/polityka-prywatnosci" target="_blank" className="text-primary-400 hover:text-primary-300 underline">
                {t('register.privacyLink', { ns: 'auth' })}
              </Link>
            </label>
          </div>
          <div className="flex items-start gap-2 rounded-lg border border-surface-700/60 bg-surface-800/40 p-3">
            <input
              id="completionMarketing"
              type="checkbox"
              checked={acceptedMarketing}
              onChange={(e) => setAcceptedMarketing(e.target.checked)}
              className="mt-0.5 h-4 w-4 rounded border-surface-600 bg-surface-800 text-primary-500 focus:ring-2 focus:ring-primary-500"
            />
            <label htmlFor="completionMarketing" className="text-sm text-surface-300">
              {t('register.acceptMarketing', { ns: 'auth' })}
              <span className="block text-xs text-surface-500 mt-0.5">{t('register.acceptMarketingHint', { ns: 'auth' })}</span>
            </label>
          </div>
        </div>
      )}

      {error && <p className="text-sm text-rose-400/80">{error}</p>}
      <Button type="submit" variant="primary" className="w-full" loading={loading}>
        {submitLabel ?? t('completion.submit')}
      </Button>
    </form>
  )
}
