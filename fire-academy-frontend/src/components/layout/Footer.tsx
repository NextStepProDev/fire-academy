import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Mail, Phone, MapPin } from 'lucide-react'
import { ShareButton } from '../ui/ShareButton'

export function Footer() {
  const { t } = useTranslation('common')
  const year = new Date().getFullYear()

  return (
    <footer className="bg-surface-950 border-t border-surface-800">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-10">
          <div>
            <img
              src="/images/logo/logo-white.png"
              alt="Fire Academy"
              className="h-10 mb-4"
            />
            <p className="text-sm text-surface-400 leading-relaxed">
              {t('footer.description')}
            </p>
          </div>

          <div>
            <h4 className="text-sm font-semibold text-surface-200 uppercase tracking-wider mb-4">
              {t('footer.quickLinks')}
            </h4>
            <ul className="space-y-2">
              <li>
                <Link to="/treningi" className="inline-block text-sm text-surface-400 hover:text-primary-400 hover:-translate-y-0.5 transition-all duration-200">
                  {t('nav.trainings')}
                </Link>
              </li>
              <li>
                <Link to="/obozy" className="inline-block text-sm text-surface-400 hover:text-primary-400 hover:-translate-y-0.5 transition-all duration-200">
                  {t('nav.camps')}
                </Link>
              </li>
              <li>
                <Link to="/szkolenia" className="inline-block text-sm text-surface-400 hover:text-primary-400 hover:-translate-y-0.5 transition-all duration-200">
                  {t('nav.courses')}
                </Link>
              </li>
            </ul>
          </div>

          <div>
            <h4 className="text-sm font-semibold text-surface-200 uppercase tracking-wider mb-4">
              Fire Academy
            </h4>
            <ul className="space-y-3">
              <li className="flex items-start gap-3">
                <MapPin className="w-4 h-4 text-primary-500 mt-0.5 shrink-0" />
                <span className="text-sm text-surface-400">
                  ul. Kanałowa 1, 40-748 Katowice
                </span>
              </li>
              <li className="flex items-center gap-3">
                <Phone className="w-4 h-4 text-primary-500 shrink-0" />
                <a href="tel:+48534823667" className="inline-block text-sm text-surface-400 hover:text-primary-400 hover:-translate-y-0.5 transition-all duration-200">
                  +48 534 823 667
                </a>
              </li>
              <li className="flex items-center gap-3">
                <Mail className="w-4 h-4 text-primary-500 shrink-0" />
                <a href="mailto:fireacademy.biz@gmail.com" className="inline-block text-sm text-surface-400 hover:text-primary-400 hover:-translate-y-0.5 transition-all duration-200">
                  fireacademy.biz@gmail.com
                </a>
              </li>
            </ul>
          </div>
        </div>

        <div className="mt-10 pt-8 border-t border-surface-800 flex flex-col sm:flex-row items-center justify-between gap-4">
          <p className="text-xs text-surface-500">
            {t('footer.copyright', { year })}
          </p>
          <div className="flex items-center gap-6">
            <Link to="/polityka-prywatnosci" className="inline-block text-xs text-surface-500 hover:text-surface-300 hover:-translate-y-0.5 transition-all duration-200">
              {t('footer.privacy')}
            </Link>
<ShareButton url="/" title="Fire Academy" />
          </div>
        </div>
      </div>
    </footer>
  )
}
