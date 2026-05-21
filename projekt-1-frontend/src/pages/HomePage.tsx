import { useTranslation } from 'react-i18next'

export function HomePage() {
  const { t } = useTranslation('common')
  return (
    <div className="max-w-4xl mx-auto px-4 py-16 text-center">
      <h1 className="text-4xl font-bold text-surface-100 mb-4">{t('home.title')}</h1>
      <p className="text-lg text-surface-400">{t('home.subtitle')}</p>
    </div>
  )
}
