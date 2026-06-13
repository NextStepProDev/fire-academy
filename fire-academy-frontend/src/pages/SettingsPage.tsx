import { useState, type FormEvent } from 'react'
import { ChevronDown } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import { authApi } from '../api/client'
import { Button } from '../components/ui/Button'

const getErrorMessage = (err: unknown) => err instanceof Error ? err.message : String(err)

export function SettingsPage() {
  const { t } = useTranslation('settings')
  const { user, refreshUser, logout } = useAuth()
  const { showToast } = useToast()
  const navigate = useNavigate()

  const [firstName, setFirstName] = useState(user?.firstName ?? '')
  const [lastName, setLastName] = useState(user?.lastName ?? '')
  const [phone, setPhone] = useState(user?.phone ?? '')
  const [profileLoading, setProfileLoading] = useState(false)
  const [profileError, setProfileError] = useState<string | null>(null)
  const [showProfileForm, setShowProfileForm] = useState(false)

  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [passwordLoading, setPasswordLoading] = useState(false)
  const [passwordError, setPasswordError] = useState<string | null>(null)
  const [showPasswordForm, setShowPasswordForm] = useState(false)

  const [deletePassword, setDeletePassword] = useState('')
  const [deleteLoading, setDeleteLoading] = useState(false)
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)

  const handleProfileSave = async (e: FormEvent) => {
    e.preventDefault()
    setProfileError(null)
    setProfileLoading(true)
    try {
      await authApi.updateProfile(firstName, lastName, phone)
      await refreshUser()
      showToast(t('profile.saved'))
    } catch (err) {
      setProfileError(getErrorMessage(err))
    } finally {
      setProfileLoading(false)
    }
  }

  const handlePasswordChange = async (e: FormEvent) => {
    e.preventDefault()
    setPasswordError(null)
    if (newPassword !== confirmPassword) {
      setPasswordError(t('validation.passwordsMismatch', { ns: 'errors' }))
      return
    }
    if (newPassword.length < 8) {
      setPasswordError(t('validation.passwordTooShort', { ns: 'errors' }))
      return
    }
    setPasswordLoading(true)
    try {
      await authApi.changePassword(currentPassword, newPassword)
      setCurrentPassword('')
      setNewPassword('')
      setConfirmPassword('')
      showToast(t('password.changed'))
    } catch (err) {
      setPasswordError(getErrorMessage(err))
    } finally {
      setPasswordLoading(false)
    }
  }

  const handleNotificationsToggle = async () => {
    try {
      await authApi.updateNotifications(!user?.emailNotificationsEnabled)
      await refreshUser()
      showToast(t('notifications.updated'))
    } catch (err) {
      showToast(getErrorMessage(err), 'error')
    }
  }

  const handleDeleteAccount = async () => {
    setDeleteLoading(true)
    try {
      await authApi.deleteAccount(user?.hasPassword ? deletePassword : null)
      logout()
      navigate('/', { replace: true })
    } catch (err) {
      showToast(getErrorMessage(err), 'error')
      setDeleteLoading(false)
    }
  }

  // "Zapisz" / "Anuluj" aktywne tylko, gdy faktycznie coś zmieniono względem zapisanych danych.
  const isProfileDirty =
    firstName !== (user?.firstName ?? '') ||
    lastName !== (user?.lastName ?? '') ||
    phone !== (user?.phone ?? '')

  const resetProfile = () => {
    setFirstName(user?.firstName ?? '')
    setLastName(user?.lastName ?? '')
    setPhone(user?.phone ?? '')
    setProfileError(null)
  }

  const isPasswordDirty =
    currentPassword !== '' || newPassword !== '' || confirmPassword !== ''

  const resetPasswordForm = () => {
    setCurrentPassword('')
    setNewPassword('')
    setConfirmPassword('')
    setPasswordError(null)
  }

  const inputClass = 'w-full px-3 py-2 bg-surface-800 border border-surface-700 rounded-lg text-surface-100 placeholder-surface-500 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent'

  return (
    <div className="max-w-2xl mx-auto px-4 py-8">
      <h1 className="text-2xl font-bold text-surface-100 mb-8">{t('title')}</h1>

      <section className="bg-surface-900 rounded-xl p-6 border border-surface-800 mb-6">
        <button
          type="button"
          onClick={() => {
            const next = !showProfileForm
            setShowProfileForm(next)
            if (!next) resetProfile()
          }}
          aria-expanded={showProfileForm}
          className="w-full flex items-center justify-between text-left cursor-pointer group"
        >
          <h2 className="text-lg font-semibold text-surface-100 group-hover:text-primary-300 transition-colors">{t('profile.title')}</h2>
          <ChevronDown className={`w-5 h-5 text-surface-400 group-hover:text-primary-300 transition-all ${showProfileForm ? 'rotate-180' : ''}`} />
        </button>
        {showProfileForm && (
        <form onSubmit={handleProfileSave} className="space-y-4 mt-4">
          <div>
            <label className="block text-sm font-medium text-surface-300 mb-1">{t('profile.firstName')}</label>
            <input type="text" value={firstName} onChange={e => setFirstName(e.target.value)} className={inputClass} required />
          </div>
          <div>
            <label className="block text-sm font-medium text-surface-300 mb-1">{t('profile.lastName')}</label>
            <input type="text" value={lastName} onChange={e => setLastName(e.target.value)} className={inputClass} required />
          </div>
          <div>
            <label className="block text-sm font-medium text-surface-300 mb-1">{t('profile.phone')}</label>
            <input type="tel" value={phone} onChange={e => setPhone(e.target.value)} className={inputClass} />
          </div>
          {profileError && <p className="text-sm text-rose-400/80">{profileError}</p>}
          <div className="flex gap-3">
            <Button type="submit" loading={profileLoading} disabled={!isProfileDirty}>{t('profile.save')}</Button>
            <Button type="button" variant="secondary" onClick={resetProfile} disabled={!isProfileDirty}>{t('profile.cancel')}</Button>
          </div>
        </form>
        )}
      </section>

      {user?.hasPassword && (
        <section className="bg-surface-900 rounded-xl p-6 border border-surface-800 mb-6">
          <button
            type="button"
            onClick={() => {
              const next = !showPasswordForm
              setShowPasswordForm(next)
              if (!next) resetPasswordForm()
            }}
            aria-expanded={showPasswordForm}
            className="w-full flex items-center justify-between text-left cursor-pointer group"
          >
            <h2 className="text-lg font-semibold text-surface-100 group-hover:text-primary-300 transition-colors">{t('password.title')}</h2>
            <ChevronDown className={`w-5 h-5 text-surface-400 group-hover:text-primary-300 transition-all ${showPasswordForm ? 'rotate-180' : ''}`} />
          </button>
          {showPasswordForm && (
          <form onSubmit={handlePasswordChange} className="space-y-4 mt-4">
            <div>
              <label className="block text-sm font-medium text-surface-300 mb-1">{t('password.current')}</label>
              <input type="password" value={currentPassword} onChange={e => setCurrentPassword(e.target.value)} className={inputClass} required />
            </div>
            <div>
              <label className="block text-sm font-medium text-surface-300 mb-1">{t('password.new')}</label>
              <input type="password" value={newPassword} onChange={e => setNewPassword(e.target.value)} className={inputClass} required />
            </div>
            <div>
              <label className="block text-sm font-medium text-surface-300 mb-1">{t('password.confirm')}</label>
              <input type="password" value={confirmPassword} onChange={e => setConfirmPassword(e.target.value)} className={inputClass} required />
            </div>
            {passwordError && <p className="text-sm text-rose-400/80">{passwordError}</p>}
            <div className="flex gap-3">
              <Button type="submit" loading={passwordLoading} disabled={!isPasswordDirty}>{t('password.change')}</Button>
              <Button type="button" variant="secondary" onClick={resetPasswordForm} disabled={!isPasswordDirty}>{t('profile.cancel')}</Button>
            </div>
          </form>
          )}
        </section>
      )}

      <section className="bg-surface-900 rounded-xl p-6 border border-surface-800 mb-6">
        <h2 className="text-lg font-semibold text-surface-100 mb-4">{t('notifications.title')}</h2>
        <div className="flex items-center justify-between">
          <span className="text-surface-300">{t('notifications.email')}</span>
          <button
            onClick={handleNotificationsToggle}
            className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors ${
              user?.emailNotificationsEnabled ? 'bg-primary-600' : 'bg-surface-600'
            }`}
          >
            <span
              className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${
                user?.emailNotificationsEnabled ? 'translate-x-6' : 'translate-x-1'
              }`}
            />
          </button>
        </div>
      </section>

      <section className="bg-surface-900 rounded-xl p-6 border border-rose-500/20">
        <h2 className="text-lg font-semibold text-rose-400 mb-4">{t('danger.title')}</h2>
        {!showDeleteConfirm ? (
          <Button variant="danger" onClick={() => setShowDeleteConfirm(true)}>
            {t('danger.deleteAccount')}
          </Button>
        ) : (
          <div className="space-y-4">
            <p className="text-surface-300 text-sm">{t('danger.deleteConfirm')}</p>
            {user?.hasPassword && (
              <div>
                <label className="block text-sm font-medium text-surface-300 mb-1">{t('danger.passwordRequired')}</label>
                <input type="password" value={deletePassword} onChange={e => setDeletePassword(e.target.value)} className={inputClass} />
              </div>
            )}
            <div className="flex gap-3">
              <Button variant="danger" onClick={handleDeleteAccount} loading={deleteLoading}>
                {t('danger.deleteAccount')}
              </Button>
              <Button variant="secondary" onClick={() => setShowDeleteConfirm(false)}>
                Cancel
              </Button>
            </div>
          </div>
        )}
      </section>
    </div>
  )
}
