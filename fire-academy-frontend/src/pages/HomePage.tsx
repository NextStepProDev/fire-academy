import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'

const sections = [
  { key: 'trainings', to: '/treningi', bg: 'https://images.unsplash.com/photo-1571019614242-c5c5dee9f50b?w=1200&q=80' },
  { key: 'camps', to: '/obozy', bg: 'https://images.unsplash.com/photo-1526506118085-60ce8714f8c5?w=1200&q=80' },
  { key: 'courses', to: '/szkolenia', bg: 'https://images.unsplash.com/photo-1549576490-b0b4831ef60a?w=1200&q=80' },
] as const

export function HomePage() {
  const { t } = useTranslation('common')

  return (
    <div className="relative w-full h-[calc(100vh-4rem)] overflow-hidden">
      {sections.map((section, i) => {
        const clipPaths = [
          'polygon(0 0, 100% 0, 100% 38%, 0 32%)',
          'polygon(0 30%, 100% 36%, 100% 72%, 0 66%)',
          'polygon(0 64%, 100% 70%, 100% 100%, 0 100%)',
        ]

        return (
          <Link
            key={section.key}
            to={section.to}
            className="absolute inset-0 group"
            style={{ clipPath: clipPaths[i] }}
          >
            <div
              className="absolute inset-0 bg-cover bg-center transition-transform duration-500 group-hover:scale-110"
              style={{ backgroundImage: `url(${section.bg})` }}
            />
            <div className="absolute inset-0 bg-black/55 group-hover:bg-black/40 transition-colors duration-300" />
            <div className="absolute inset-0 flex items-center justify-center">
              <h2 className="text-4xl md:text-6xl font-bold text-white tracking-wider uppercase drop-shadow-lg group-hover:text-primary-400 transition-colors duration-300">
                {t(`home.section.${section.key}`)}
              </h2>
            </div>
          </Link>
        )
      })}
    </div>
  )
}
