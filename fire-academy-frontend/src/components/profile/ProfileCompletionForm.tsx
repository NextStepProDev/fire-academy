import { useState, type FormEvent } from 'react'
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
 * Formularz uzupełniania brakujących, wymaganych pól profilu (imię, nazwisko, telefon).
 * Renderuje wyłącznie pola, których brakuje, zapisuje je na koncie i odświeża usera
 * w `AuthContext`. Komponent nadrzędny reaguje na zmianę usera (braki znikają), więc
 * nie potrzebuje callbacku — zapis na wydarzenie / przekierowanie obsługuje się tam.
 */
export function ProfileCompletionForm({ submitLabel }: ProfileCompletionFormProps) {
  const { t } = useTranslation('settings')
  const { user, refreshUser } = useAuth()
  const missing = getMissingProfileFields(user)
  const [values, setValues] = useState<Record<ProfileField, string>>({ firstName: '', lastName: '', phone: '' })
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  if (!user || missing.length === 0) return null

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

    // Pola spoza listy braków zachowują dotychczasowe wartości z konta.
    const payload = {
      firstName: missing.includes('firstName') ? values.firstName.trim() : user.firstName,
      lastName: missing.includes('lastName') ? values.lastName.trim() : user.lastName,
      phone: missing.includes('phone') ? values.phone.trim() : (user.phone ?? ''),
    }

    setLoading(true)
    try {
      await authApi.updateProfile(payload.firstName, payload.lastName, payload.phone)
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
      {error && <p className="text-sm text-rose-400/80">{error}</p>}
      <Button type="submit" variant="primary" className="w-full" loading={loading}>
        {submitLabel ?? t('completion.submit')}
      </Button>
    </form>
  )
}
