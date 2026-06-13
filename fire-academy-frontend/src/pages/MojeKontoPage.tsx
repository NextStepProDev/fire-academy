import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { CalendarCheck, Pencil, User as UserIcon } from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import { Seo } from '../components/seo/Seo'
import { Button } from '../components/ui/Button'

export function MojeKontoPage() {
  const { t } = useTranslation('account')
  const { user } = useAuth()

  if (!user) return null

  return (
    <div className="max-w-3xl mx-auto px-4 py-10 space-y-8">
      <Seo title={t('title')} path="/moje-konto" />

      <div>
        <h1 className="text-3xl md:text-4xl font-bold text-surface-100">{t('title')}</h1>
        <p className="text-surface-400 mt-2">{t('greeting', { name: user.firstName })}</p>
      </div>

      {/* Profil */}
      <section className="bg-surface-900 rounded-xl p-6 border border-surface-800">
        <div className="flex items-center justify-between mb-5">
          <h2 className="flex items-center gap-2 text-xl font-bold text-surface-100">
            <UserIcon className="w-5 h-5 text-primary-400" />
            {t('profile.title')}
          </h2>
          <Link to="/settings">
            <Button variant="secondary" className="!px-3 !py-1.5 text-sm">
              <Pencil className="w-4 h-4 mr-1.5" />
              {t('profile.edit')}
            </Button>
          </Link>
        </div>

        <dl className="grid grid-cols-1 sm:grid-cols-2 gap-x-8 gap-y-4">
          <div>
            <dt className="text-xs uppercase tracking-wide text-surface-500">{t('profile.firstName')}</dt>
            <dd className="text-surface-100 mt-0.5">{user.firstName}</dd>
          </div>
          <div>
            <dt className="text-xs uppercase tracking-wide text-surface-500">{t('profile.lastName')}</dt>
            <dd className="text-surface-100 mt-0.5">{user.lastName}</dd>
          </div>
          <div>
            <dt className="text-xs uppercase tracking-wide text-surface-500">{t('profile.email')}</dt>
            <dd className="text-surface-100 mt-0.5 break-all">{user.email}</dd>
          </div>
          <div>
            <dt className="text-xs uppercase tracking-wide text-surface-500">{t('profile.phone')}</dt>
            <dd className="text-surface-100 mt-0.5">{user.phone || t('profile.noPhone')}</dd>
          </div>
        </dl>
      </section>

      {/* Moje rezerwacje (placeholder — logika zapisów w kolejnym etapie) */}
      <section className="bg-surface-900 rounded-xl p-6 border border-surface-800">
        <h2 className="flex items-center gap-2 text-xl font-bold text-surface-100 mb-5">
          <CalendarCheck className="w-5 h-5 text-primary-400" />
          {t('reservations.title')}
        </h2>

        <div className="flex flex-col items-center text-center py-8 px-4 rounded-lg border border-dashed border-surface-700">
          <p className="text-surface-300 font-medium">{t('reservations.empty')}</p>
          <p className="text-surface-500 text-sm mt-1 max-w-md">{t('reservations.emptyHint')}</p>
          <Link to="/treningi" className="mt-5">
            <Button variant="primary">{t('reservations.browse')}</Button>
          </Link>
        </div>
      </section>
    </div>
  )
}
