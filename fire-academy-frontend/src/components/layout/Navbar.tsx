import { Link, useLocation, useNavigate } from 'react-router-dom'
import { LogOut, LogIn, Menu, User, X, ChevronDown } from 'lucide-react'
import { useState, useRef, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../../context/AuthContext'
import { Avatar } from '../ui/Avatar'
import clsx from 'clsx'

export function Navbar() {
  const { t } = useTranslation('common')
  const { user, isAuthenticated, isAdmin, logout } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)
  const [userMenuOpen, setUserMenuOpen] = useState(false)
  const [navHidden, setNavHidden] = useState(false)
  const lastScrollY = useRef(0)
  const userMenuRef = useRef<HTMLDivElement>(null)

  const isLinkActive = (path: string) =>
    path === '/' ? location.pathname === '/' : location.pathname.startsWith(path)

  // Zalogowany użytkownik widzi swoje menu (avatar + ustawienia + wyloguj) na całej stronie.
  // Zapis na wydarzenia wymaga konta, więc punkt wejścia do logowania pokazujemy GOŚCIOWI
  // na każdej zakładce (treningi/obozy/szkolenia i pozostałe).
  const showUserArea = isAuthenticated
  const showLoginEntry = !isAuthenticated

  // "Ustawienia" w menu avatara podświetlamy, gdy user jest na tej podstronie ("tu jesteś").
  const isSettingsActive = location.pathname.startsWith('/settings')

  const navLinks = [
    { to: '/', label: t('nav.home') },
    { to: '/treningi', label: t('nav.trainings') },
    { to: '/obozy', label: t('nav.camps') },
    { to: '/szkolenia', label: t('nav.courses') },
    // "Moje konto" — główny cel zalogowanego usera — na stałe w nawbarze (poza adminem).
    ...(isAuthenticated && !isAdmin ? [{ to: '/moje-konto', label: t('nav.myAccount') }] : []),
    ...(isAdmin ? [{ to: '/admin', label: t('nav.admin') }] : []),
  ]

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (userMenuRef.current && !userMenuRef.current.contains(e.target as Node)) {
        setUserMenuOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  useEffect(() => {
    function onScroll() {
      const y = window.scrollY
      if (y < 60) {
        setNavHidden(false)
      } else if (y > lastScrollY.current + 5) {
        setNavHidden(true)
      } else if (y < lastScrollY.current - 5) {
        setNavHidden(false)
      }
      lastScrollY.current = y
    }
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [])

  const [prevPathname, setPrevPathname] = useState(location.pathname)
  if (prevPathname !== location.pathname) {
    setPrevPathname(location.pathname)
    setUserMenuOpen(false)
  }

  return (
    <nav className={clsx('bg-surface-900/80 backdrop-blur-sm border-b border-surface-800 sticky top-0 z-50 transition-transform duration-300', navHidden && !mobileMenuOpen && '-translate-y-full')}>
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-16">
          <Link to="/" className="hover:opacity-80 transition-opacity">
            <img
              src="/images/logo/logo-white.png"
              alt="Fire Academy"
              className="h-9"
            />
          </Link>

          <div className="hidden md:flex items-center gap-1">
            {navLinks.map((link) => (
              <Link
                key={link.to}
                to={link.to}
                className={clsx(
                  'px-3 py-1.5 rounded-lg text-base font-semibold tracking-wide transition-all duration-150 active:scale-95',
                  isLinkActive(link.to)
                    ? 'text-surface-100'
                    : 'text-surface-400 hover:bg-surface-800/60 hover:text-surface-200'
                )}
              >
                {link.label}
              </Link>
            ))}
          </div>

          {showUserArea && (
            <div className="hidden md:flex items-center gap-2">
              <div className="relative" ref={userMenuRef}>
                <button
                  onClick={() => setUserMenuOpen(!userMenuOpen)}
                  className="flex items-center gap-3 px-3 py-2 rounded-lg hover:bg-surface-800 active:scale-95 transition-all duration-150"
                >
                  <Avatar src={user?.avatarUrl} name={user?.firstName} className="w-9 h-9" />

                  <span className="text-sm font-medium text-surface-200">{user?.firstName}</span>
                  <ChevronDown className={clsx('w-4 h-4 text-surface-400 transition-transform', userMenuOpen && 'rotate-180')} />
                </button>

                {userMenuOpen && (
                  <div className="absolute right-0 mt-2 w-56 bg-surface-900 border border-surface-700 rounded-xl shadow-lg shadow-black/30 overflow-hidden">
                    <div className="px-4 py-3 border-b border-surface-800">
                      <p className="text-sm font-medium text-surface-100">{user?.firstName} {user?.lastName}</p>
                      <p className="text-xs text-surface-500 mt-0.5">{user?.email}</p>
                    </div>
                    <div className="py-1">
                      <button
                        onClick={() => { setUserMenuOpen(false); if (!isSettingsActive) navigate('/settings') }}
                        aria-current={isSettingsActive ? 'page' : undefined}
                        className={clsx(
                          'w-full flex items-center gap-3 px-4 py-2.5 text-sm transition-colors',
                          isSettingsActive
                            ? 'bg-primary-500/10 text-primary-400 cursor-default'
                            : 'text-surface-300 hover:bg-surface-800 hover:text-surface-100'
                        )}
                      >
                        <User className="w-4 h-4" />
                        {t('nav.settings')}
                      </button>
                      <button
                        onClick={() => { setUserMenuOpen(false); logout() }}
                        className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-rose-400/70 hover:bg-surface-800 hover:text-rose-300/80 transition-colors"
                      >
                        <LogOut className="w-4 h-4" />
                        {t('nav.logout')}
                      </button>
                    </div>
                  </div>
                )}
              </div>
            </div>
          )}

          {showLoginEntry && (
            <Link
              to="/logowanie"
              className="hidden md:flex items-center gap-2 px-3 py-2 rounded-lg text-sm font-semibold text-surface-200 hover:bg-surface-800 active:scale-95 transition-all duration-150"
            >
              <LogIn className="w-4 h-4" />
              {t('nav.login')}
            </Link>
          )}

          <button
            className="md:hidden -mr-2 p-2 text-surface-300 active:scale-90 transition-transform duration-150"
            aria-label={mobileMenuOpen ? t('nav.closeMenu') : t('nav.openMenu')}
            onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
          >
            {mobileMenuOpen ? <X className="w-6 h-6 pointer-events-none" /> : <Menu className="w-6 h-6 pointer-events-none" />}
          </button>
        </div>
      </div>

      {mobileMenuOpen && (
        <div className="md:hidden bg-surface-900 border-t border-surface-800 max-h-[calc(100dvh-4rem)] overflow-y-auto">
          <div className="px-4 py-4 space-y-3">
            {navLinks.map((link) => (
              <Link
                key={link.to}
                to={link.to}
                onClick={() => setMobileMenuOpen(false)}
                className={clsx(
                  'block py-2 text-base font-semibold tracking-wide',
                  isLinkActive(link.to) ? 'text-primary-400' : 'text-surface-300'
                )}
              >
                {link.label}
              </Link>
            ))}
            {showUserArea && (
              <div className="pt-4 border-t border-surface-800 space-y-1">
                <div className="flex items-center gap-3 px-1 py-2">
                  <Avatar src={user?.avatarUrl} name={user?.firstName} className="w-9 h-9" />

                  <div>
                    <p className="text-sm font-medium text-surface-200">{user?.firstName} {user?.lastName}</p>
                    <p className="text-xs text-surface-500">{user?.email}</p>
                  </div>
                </div>
                <Link
                  to="/settings"
                  onClick={() => setMobileMenuOpen(false)}
                  aria-current={isSettingsActive ? 'page' : undefined}
                  className={clsx(
                    'flex items-center gap-3 px-1 py-2 text-sm',
                    isSettingsActive ? 'text-primary-400 font-medium' : 'text-surface-300'
                  )}
                >
                  <User className="w-4 h-4" />
                  {t('nav.settings')}
                </Link>
                <button
                  onClick={() => { setMobileMenuOpen(false); logout() }}
                  className="flex items-center gap-3 px-1 py-2 text-rose-400/70 text-sm"
                >
                  <LogOut className="w-4 h-4" />
                  {t('nav.logout')}
                </button>
              </div>
            )}

            {showLoginEntry && (
              <div className="pt-4 border-t border-surface-800">
                <Link
                  to="/logowanie"
                  onClick={() => setMobileMenuOpen(false)}
                  className="flex items-center gap-3 px-1 py-2 text-surface-200 text-sm font-semibold"
                >
                  <LogIn className="w-4 h-4" />
                  {t('nav.login')}
                </Link>
              </div>
            )}
          </div>
        </div>
      )}
    </nav>
  )
}
