import { useTranslation } from 'react-i18next'

export function TrainingsPage() {
  const { t } = useTranslation('events')
  return (
    <div className="max-w-4xl mx-auto px-4 py-16 text-center">
      <h1 className="text-3xl font-bold text-surface-100 mb-4">{t('trainings.title')}</h1>
      <p className="text-lg text-surface-400">{t('trainings.comingSoon')}</p>
    </div>
  )
}
