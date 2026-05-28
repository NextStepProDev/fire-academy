import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'

const sections = [
  {
    key: 'trainings',
    to: '/treningi',
    bg: 'https://images.unsplash.com/photo-1575800605380-ca1d27744f2c?w=1920&q=80',
    clipPath: 'polygon(0 0, 100% 0, 100% 40%, 0 34%)',
    textTop: '17%',
    overlayClass: 'bg-black/55 group-hover:bg-black/40',
    imgArea: { top: '-10%', bottom: '50%' },
  },
  {
    key: 'camps',
    to: '/obozy',
    bg: 'https://images.unsplash.com/photo-1578592391689-0e3d1a1b52b9?w=1920&q=80',
    clipPath: 'polygon(0 31%, 100% 37%, 100% 73%, 0 67%)',
    textTop: '52%',
    overlayClass: 'bg-black/30 group-hover:bg-black/15',
    imgArea: { top: '20%', bottom: '15%' },
  },
  {
    key: 'courses',
    to: '/szkolenia',
    bg: 'https://images.unsplash.com/photo-1517836357463-d25dfeac3438?w=1920&q=80',
    clipPath: 'polygon(0 64%, 100% 70%, 100% 100%, 0 100%)',
    textTop: '83%',
    overlayClass: 'bg-black/35 group-hover:bg-black/20',
    imgArea: { top: '50%', bottom: '-15%' },
  },
] as const

const separators = [
  'polygon(0 30.9%, 100% 36.9%, 100% 37.1%, 0 31.1%)',
  'polygon(0 63.9%, 100% 69.9%, 100% 70.1%, 0 64.1%)',
]

export function HomePage() {
  const { t } = useTranslation('common')

  return (
    <div className="relative w-full h-screen overflow-hidden">
      {sections.map((section) => (
        <Link
          key={section.key}
          to={section.to}
          className="absolute inset-0 group"
          style={{ clipPath: section.clipPath }}
        >
          <div
            className="absolute left-0 right-0 bg-cover bg-center transition-transform duration-500 group-hover:scale-110"
            style={{
              backgroundImage: `url(${section.bg})`,
              top: section.imgArea.top,
              bottom: section.imgArea.bottom,
            }}
          />
          <div className={`absolute inset-0 ${section.overlayClass} transition-colors duration-300`} />
          <div
            className="absolute left-0 right-0 flex justify-center -translate-y-1/2"
            style={{ top: section.textTop }}
          >
            <h2 className="text-5xl md:text-7xl font-bold text-white tracking-wider uppercase drop-shadow-lg group-hover:text-primary-400 transition-colors duration-300">
              {t(`home.section.${section.key}`)}
            </h2>
          </div>
        </Link>
      ))}
      {separators.map((clipPath) => (
        <div
          key={clipPath}
          className="absolute inset-0 bg-primary-500 pointer-events-none z-10"
          style={{ clipPath }}
        />
      ))}
    </div>
  )
}
