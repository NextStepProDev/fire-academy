import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../context/AuthContext'
import { consumeRedirectPath } from '../utils/redirect'
import { needsProfileCompletion } from '../utils/profileCompletion'
import { ProfileCompletionForm } from '../components/profile/ProfileCompletionForm'

/**
 * Strona uzupełniania profilu po rejestracji/logowaniu, gdy brakuje wymaganych danych
 * (typowo telefonu po rejestracji przez Google). Po zapisaniu danych user zostaje
 * przekierowany tam, skąd przyszedł (returnTo) lub na „Moje konto".
 */
export function ProfileCompletionPage() {
  const { t } = useTranslation('settings')
  const { user } = useAuth()
  const navigate = useNavigate()
  const incomplete = needsProfileCompletion(user)

  useEffect(() => {
    // Profil kompletny i zgody domknięte (wejście wprost lub po zapisaniu) → kontynuuj ścieżkę.
    if (user && !incomplete) {
      navigate(consumeRedirectPath() || '/moje-konto', { replace: true })
    }
  }, [user, incomplete, navigate])

  if (!user || !incomplete) return null

  return (
    <div className="max-w-md mx-auto px-4 py-12">
      <h1 className="text-2xl font-bold text-surface-100">{t('completion.pageTitle')}</h1>
      <p className="text-surface-400 mt-2 mb-6">{t('completion.pageText')}</p>
      <ProfileCompletionForm />
    </div>
  )
}
