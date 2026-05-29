import { Link, useLocation, useNavigate } from 'react-router-dom'
import { LogOut, Menu, User, X, ChevronDown } from 'lucide-react'
import { useState, useRef, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../../context/AuthContext'
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

  const navLinks = [
    { to: '/', label: t('nav.home') },
    { to: '/treningi', label: t('nav.trainings') },
    { to: '/obozy', label: t('nav.camps') },
    { to: '/szkolenia', label: t('nav.courses') },
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

  const userInitial = user?.firstName?.charAt(0).toUpperCase() ?? '?'

  return (
    <nav className={clsx('bg-surface-900/80 backdrop-blur-sm border-b border-surface-800 sticky top-0 z-50 transition-transform duration-300', navHidden && !mobileMenuOpen && '-translate-y-full')}>
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-16">
          <Link to="/" className="text-xl font-bold text-surface-100 hover:opacity-80 transition-opacity">
            Fire Academy
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

          {isAuthenticated && isAdmin && (
            <div className="hidden md:flex items-center gap-2">
              <div className="relative" ref={userMenuRef}>
                <button
                  onClick={() => setUserMenuOpen(!userMenuOpen)}
                  className="flex items-center gap-3 px-3 py-2 rounded-lg hover:bg-surface-800 active:scale-95 transition-all duration-150"
                >
                  <div className="w-9 h-9 rounded-full bg-primary-600 flex items-center justify-center">
                    <span className="text-sm font-bold text-white">{userInitial}</span>
                  </div>
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
                        onClick={() => navigate('/settings')}
                        className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-surface-300 hover:bg-surface-800 hover:text-surface-100 transition-colors"
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

          <button
            className="md:hidden text-surface-300 active:scale-90 transition-transform duration-150"
            aria-label={mobileMenuOpen ? t('nav.closeMenu') : t('nav.openMenu')}
            onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
          >
            {mobileMenuOpen ? <X className="w-6 h-6" /> : <Menu className="w-6 h-6" />}
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
            {isAuthenticated && isAdmin && (
              <div className="pt-4 border-t border-surface-800 space-y-1">
                <div className="flex items-center gap-3 px-1 py-2">
                  <div className="w-9 h-9 rounded-full bg-primary-600 flex items-center justify-center">
                    <span className="text-sm font-bold text-white">{userInitial}</span>
                  </div>
                  <div>
                    <p className="text-sm font-medium text-surface-200">{user?.firstName} {user?.lastName}</p>
                    <p className="text-xs text-surface-500">{user?.email}</p>
                  </div>
                </div>
                <Link
                  to="/settings"
                  onClick={() => setMobileMenuOpen(false)}
                  className="flex items-center gap-3 px-1 py-2 text-surface-300 text-sm"
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
          </div>
        </div>
      )}
    </nav>
  )
}
