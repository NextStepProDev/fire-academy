import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { ArrowRight, CalendarCheck, Dumbbell, Pencil, User as UserIcon } from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import { userApi } from '../api/client'
import { userApi as trainingApi } from '../api/user'
import { Seo } from '../components/seo/Seo'
import { Button } from '../components/ui/Button'
import { Avatar } from '../components/ui/Avatar'

export function MojeKontoPage() {
  const { t } = useTranslation('account')
  const { user } = useAuth()

  const enrollmentsQuery = useQuery({
    queryKey: ['user', 'enrollments'],
    queryFn: () => userApi.getMyEnrollments(),
  })

  const trainingsQuery = useQuery({
    queryKey: ['user', 'training-enrollments'],
    queryFn: trainingApi.getMyTrainingEnrollments,
  })

  if (!user) return null

  const reservationsCount = (enrollmentsQuery.data?.current.length ?? 0) + (enrollmentsQuery.data?.past.length ?? 0)
  const trainingsCount = trainingsQuery.data?.length ?? 0

  return (
    <div className="max-w-3xl mx-auto px-4 py-10 space-y-8">
      <Seo title={t('title')} path="/moje-konto" />

      <div className="flex items-center gap-5">
        <Avatar
          src={user.avatarUrl}
          name={user.firstName}
          className="w-20 h-20"
          textClassName="text-2xl"
        />
        <div>
          <h1 className="text-3xl md:text-4xl font-bold text-surface-100">{t('title')}</h1>
          <p className="text-surface-400 mt-2">{t('greeting', { name: user.firstName })}</p>
        </div>
      </div>

      {/* Profile */}
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

      {/* Two tiles: event reservations vs cyclical trainings */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
        <AccountTile
          to="/moje-konto/rezerwacje"
          icon={<CalendarCheck className="w-6 h-6" />}
          title={t('tiles.reservations.title')}
          description={t('tiles.reservations.description')}
          count={enrollmentsQuery.isSuccess ? reservationsCount : undefined}
          countLabel={t('tiles.reservations.count', { count: reservationsCount })}
          cta={t('tiles.open')}
        />
        <AccountTile
          to="/moje-konto/treningi"
          icon={<Dumbbell className="w-6 h-6" />}
          title={t('tiles.trainings.title')}
          description={t('tiles.trainings.description')}
          count={trainingsQuery.isSuccess ? trainingsCount : undefined}
          countLabel={t('tiles.trainings.count', { count: trainingsCount })}
          cta={t('tiles.open')}
        />
      </div>
    </div>
  )
}

interface AccountTileProps {
  to: string
  icon: React.ReactNode
  title: string
  description: string
  count?: number
  countLabel: string
  cta: string
}

function AccountTile({ to, icon, title, description, count, countLabel, cta }: AccountTileProps) {
  return (
    <Link
      to={to}
      className="group relative flex flex-col rounded-xl border border-surface-800 bg-surface-900 p-6 transition-all duration-200 hover:-translate-y-0.5 hover:border-primary-600/50 hover:bg-surface-800/60 hover:shadow-lg hover:shadow-primary-950/30 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-400"
    >
      <div className="flex items-start justify-between gap-3">
        <span className="inline-flex h-12 w-12 items-center justify-center rounded-lg bg-primary-600/15 text-primary-400 transition-colors group-hover:bg-primary-600/25">
          {icon}
        </span>
        {count != null && (
          <span className="inline-flex min-w-7 items-center justify-center rounded-full bg-primary-600/15 px-2 py-0.5 text-sm font-semibold text-primary-300">
            {count}
          </span>
        )}
      </div>
      <h3 className="mt-4 text-lg font-bold text-surface-100">{title}</h3>
      <p className="mt-1 text-sm text-surface-400">{description}</p>
      <div className="mt-4 flex items-center justify-between">
        <span className="text-xs text-surface-500">{countLabel}</span>
        <span className="inline-flex items-center gap-1 text-sm font-medium text-primary-400 transition-transform group-hover:translate-x-0.5">
          {cta}
          <ArrowRight className="w-4 h-4" />
        </span>
      </div>
    </Link>
  )
}
