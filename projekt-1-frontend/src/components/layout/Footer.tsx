import { useTranslation } from 'react-i18next'

export function Footer() {
  const { t } = useTranslation('common')
  return (
    <footer className="bg-surface-900 border-t border-surface-800 py-6">
      <div className="max-w-7xl mx-auto px-4 text-center text-sm text-surface-500">
        {t('footer.copyright', { year: new Date().getFullYear() })}
      </div>
    </footer>
  )
}
