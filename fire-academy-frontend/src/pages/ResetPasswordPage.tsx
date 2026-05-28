import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { resetPassword } from '../api/auth'
import { Button } from '../components/ui/Button'

const getErrorMessage = (err: unknown) => err instanceof Error ? err.message : String(err)

export function ResetPasswordPage() {
  const { t } = useTranslation('auth')
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token') ?? ''
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState(false)
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)

    if (newPassword.length < 8) {
      setError(t('resetPassword.tooShort'))
      return
    }
    if (newPassword !== confirmPassword) {
      setError(t('resetPassword.mismatch'))
      return
    }

    setLoading(true)
    try {
      await resetPassword(token, newPassword)
      setSuccess(true)
    } catch (err) {
      setError(getErrorMessage(err))
    } finally {
      setLoading(false)
    }
  }

  if (success) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh] px-4">
        <div className="bg-surface-900 rounded-xl p-8 max-w-md w-full border border-surface-800 text-center">
          <div className="w-12 h-12 bg-green-500/20 rounded-full flex items-center justify-center mx-auto mb-4">
            <svg className="w-6 h-6 text-green-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
            </svg>
          </div>
          <h2 className="text-xl font-bold text-surface-100 mb-2">{t('resetPassword.successTitle')}</h2>
          <p className="text-surface-400 mb-6">{t('resetPassword.successMessage')}</p>
          <Link to="/admin/login" className="text-primary-400 hover:text-primary-300 font-medium">
            {t('resetPassword.goToLogin')}
          </Link>
        </div>
      </div>
    )
  }

  return (
    <div className="flex flex-col items-center justify-center min-h-[60vh] px-4">
      <div className="bg-surface-900 rounded-xl p-8 max-w-md w-full border border-surface-800">
        <h1 className="text-2xl font-bold text-surface-100 mb-6 text-center">{t('resetPassword.title')}</h1>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="newPassword" className="block text-sm font-medium text-surface-300 mb-1">
              {t('resetPassword.newPassword')}
            </label>
            <input
              id="newPassword"
              type="password"
              required
              minLength={8}
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              className="w-full px-3 py-2 bg-surface-800 border border-surface-700 rounded-lg text-surface-100 placeholder-surface-500 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent"
            />
            <p className="text-xs text-surface-500 mt-1">{t('resetPassword.passwordHint')}</p>
          </div>

          <div>
            <label htmlFor="confirmPassword" className="block text-sm font-medium text-surface-300 mb-1">
              {t('resetPassword.confirmPassword')}
            </label>
            <input
              id="confirmPassword"
              type="password"
              required
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              className="w-full px-3 py-2 bg-surface-800 border border-surface-700 rounded-lg text-surface-100 placeholder-surface-500 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent"
            />
          </div>

          {error && <p className="text-sm text-rose-400/80">{error}</p>}

          <Button type="submit" variant="primary" className="w-full" loading={loading}>
            {t('resetPassword.submit')}
          </Button>
        </form>
      </div>
    </div>
  )
}
