import { useTranslation } from 'react-i18next'

export function AdminPage() {
  const { t } = useTranslation('common')
  return (
    <div className="max-w-4xl mx-auto px-4 py-16">
      <h1 className="text-2xl font-bold text-surface-100 mb-4">{t('admin.title')}</h1>
      <p className="text-surface-400">{t('admin.placeholder')}</p>
    </div>
  )
}
