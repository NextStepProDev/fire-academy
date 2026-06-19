import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import { authApi } from '../api/client'
import { consumeRedirectPath } from '../utils/redirect'
import { needsProfileCompletion } from '../utils/profileCompletion'
import { ProfileCompletionForm } from '../components/profile/ProfileCompletionForm'
import { Button } from '../components/ui/Button'

/**
 * Strona uzupełniania profilu po rejestracji/logowaniu, gdy brakuje wymaganych danych
 * (typowo telefonu po rejestracji przez Google) lub niezaakceptowanej polityki prywatności.
 * Pełni rolę twardej bramki (patrz Layout): bez domknięcia konta user nie wejdzie nigdzie indziej.
 * Gdy polityka nie jest zaakceptowana, oferujemy też wyjście „nie akceptuję — usuń konto",
 * by user mógł skasować swoje dane bez konieczności korzystania z reszty aplikacji (RODO).
 * Po zapisaniu danych user zostaje przekierowany tam, skąd przyszedł (returnTo) lub na „Moje konto".
 */
export function ProfileCompletionPage() {
  const { t } = useTranslation('settings')
  const { user, logout } = useAuth()
  const { showToast } = useToast()
  const navigate = useNavigate()
  const incomplete = needsProfileCompletion(user)
  const needPrivacy = !!user && !user.privacyAccepted
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const [deleting, setDeleting] = useState(false)

  useEffect(() => {
    // Profil kompletny i zgody domknięte (wejście wprost lub po zapisaniu) → kontynuuj ścieżkę.
    if (user && !incomplete) {
      navigate(consumeRedirectPath() || '/moje-konto', { replace: true })
    }
  }, [user, incomplete, navigate])

  if (!user || !incomplete) return null

  // Konta Google nie mają hasła, więc usunięcie nie wymaga jego potwierdzenia.
  const handleDecline = async () => {
    setDeleting(true)
    try {
      await authApi.deleteAccount(null)
      logout()
      showToast(t('completion.decline.deleted'))
      navigate('/', { replace: true })
    } catch (err) {
      showToast(err instanceof Error ? err.message : String(err), 'error')
      setDeleting(false)
    }
  }

  return (
    <div className="max-w-md mx-auto px-4 py-12">
      <h1 className="text-2xl font-bold text-surface-100">{t('completion.pageTitle')}</h1>
      <p className="text-surface-400 mt-2 mb-6">{t('completion.pageText')}</p>
      <ProfileCompletionForm />

      {needPrivacy && (
        <div className="mt-8 pt-6 border-t border-surface-800">
          <h2 className="text-sm font-semibold text-surface-200">{t('completion.decline.title')}</h2>
          <p className="text-sm text-surface-400 mt-1 mb-4">{t('completion.decline.text')}</p>
          {confirmingDelete ? (
            <div className="space-y-3">
              <p className="text-sm text-rose-400/90">{t('completion.decline.confirm')}</p>
              <div className="flex gap-3">
                <Button type="button" variant="danger" loading={deleting} onClick={handleDecline}>
                  {t('completion.decline.confirmYes')}
                </Button>
                <Button type="button" variant="ghost" disabled={deleting} onClick={() => setConfirmingDelete(false)}>
                  {t('completion.decline.cancel')}
                </Button>
              </div>
            </div>
          ) : (
            <Button type="button" variant="ghost" onClick={() => setConfirmingDelete(true)}>
              {t('completion.decline.button')}
            </Button>
          )}
        </div>
      )}
    </div>
  )
}
