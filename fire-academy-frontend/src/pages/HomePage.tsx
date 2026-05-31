import { useState, useCallback, useEffect } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Seo } from '../components/seo/Seo'
import { HeroIntro } from '../components/home/HeroIntro'

const sections = [
  {
    key: 'trainings',
    to: '/treningi',
    bg: 'https://images.unsplash.com/photo-1575800605380-ca1d27744f2c?w=1920&q=80',
    clipPath: 'polygon(0 0, 100% 0, 100% 40%, 0 34%)',
    textTop: '17%',
    overlayClass: 'bg-black/55 group-hover:bg-black/40',
    imgArea: { top: '-10%', bottom: '50%' },
    bgPosition: 'center',
  },
  {
    key: 'camps',
    to: '/obozy',
    // bg: 'https://images.unsplash.com/photo-1578592391689-0e3d1a1b52b9?w=1920&q=80',
    bg: '/images/posters/przemo-alpinizm.jpeg',
    clipPath: 'polygon(0 31%, 100% 37%, 100% 73%, 0 67%)',
    textTop: '52%',
    overlayClass: 'bg-black/30 group-hover:bg-black/15',
    imgArea: { top: '15%', bottom: '20%' },
    bgPosition: 'center 35%',
    bgPositionMobile: 'center 20%',
    imgClass: 'scale-110 group-hover:scale-[1.15]',
  },
  {
    key: 'courses',
    to: '/szkolenia',
    bg: 'https://images.unsplash.com/photo-1517836357463-d25dfeac3438?w=1920&q=80',
    clipPath: 'polygon(0 64%, 100% 70%, 100% 100%, 0 100%)',
    textTop: '83%',
    overlayClass: 'bg-black/35 group-hover:bg-black/20',
    imgArea: { top: '50%', bottom: '-15%' },
    bgPosition: 'center',
  },
] as const

const separators = [
  'polygon(0 30.9%, 100% 36.9%, 100% 37.1%, 0 31.1%)',
  'polygon(0 63.9%, 100% 69.9%, 100% 70.1%, 0 64.1%)',
]

export function HomePage() {
  const { t } = useTranslation('common')
  const [showIntro, setShowIntro] = useState(true)
  const handleIntroComplete = useCallback(() => setShowIntro(false), [])
  const [isMobile, setIsMobile] = useState(window.innerWidth < 768)
  useEffect(() => {
    const onResize = () => setIsMobile(window.innerWidth < 768)
    window.addEventListener('resize', onResize)
    return () => window.removeEventListener('resize', onResize)
  }, [])

  return (
    <div className="relative w-full h-screen overflow-hidden bg-surface-950" style={{ height: '100dvh', maxHeight: '-webkit-fill-available' }}>
      <Seo
        title="Fire Academy"
        description="Fire Academy — treningi indywidualne i małe grupy. Obozy, szkolenia i kursy dla ambitnych sportowców."
        path="/"
        jsonLd={[
          {
            '@context': 'https://schema.org',
            '@type': 'SportsActivityLocation',
            name: 'Fire Academy',
            description: 'Treningi indywidualne i małe grupy. Obozy, szkolenia i kursy dla ambitnych sportowców.',
            url: window.location.origin,
            image: `${window.location.origin}/og-default.png`,
            telephone: '+48534823667',
            '@id': `${window.location.origin}/#organization`,
          },
          {
            '@context': 'https://schema.org',
            '@type': 'WebSite',
            name: 'Fire Academy',
            url: window.location.origin,
            publisher: { '@id': `${window.location.origin}/#organization` },
            inLanguage: 'pl-PL',
          },
        ]}
      />
      {showIntro && <HeroIntro onComplete={handleIntroComplete} />}
      {sections.map((section) => (
        <Link
          key={section.key}
          to={section.to}
          className="absolute inset-0 group"
          style={{ clipPath: section.clipPath }}
        >
          <div
            className={`absolute bg-cover transition-transform duration-500 ${'imgClass' in section ? section.imgClass : 'group-hover:scale-110'} ${section.key === 'courses' ? 'courses-bg' : ''}`}
            style={{
              backgroundImage: `url(${section.bg})`,
              backgroundPosition: section.key === 'camps' && isMobile ? 'center 0%' : section.bgPosition,
              top: section.imgArea.top,
              bottom: section.imgArea.bottom,
              left: 'left' in section.imgArea ? section.imgArea.left : '0',
              right: 'right' in section.imgArea ? section.imgArea.right : '0',
            }}
          />
          <div className={`absolute inset-0 ${section.overlayClass} transition-colors duration-300`} />
          <div
            className="absolute left-0 right-0 flex justify-center -translate-y-1/2"
            style={{ top: section.textTop }}
          >
            <h2 className="text-5xl md:text-7xl font-bold text-white tracking-wider uppercase drop-shadow-lg group-hover:text-primary-400 group-active:scale-95 transition-all duration-300">
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
