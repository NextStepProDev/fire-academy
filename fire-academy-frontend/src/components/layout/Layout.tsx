import { Outlet, useLocation, Navigate } from 'react-router-dom'
import { Navbar } from './Navbar'
import { Footer } from './Footer'
import { useAuth } from '../../context/AuthContext'
import { needsProfileCompletion } from '../../utils/profileCompletion'

// Trasy dostępne dla zalogowanego usera, który nie domknął jeszcze konta (brak danych lub
// niezaakceptowana polityka prywatności). Wszystko inne jest zablokowane do czasu akceptacji
// — RODO: bez zgody na politykę nie pozwalamy korzystać z aplikacji.
const COMPLETION_ALLOWED_PATHS = ['/uzupelnij-profil', '/polityka-prywatnosci', '/oauth-callback']

export function Layout() {
  const location = useLocation()
  const isHome = location.pathname === '/'
  const { user, isLoading } = useAuth()

  // Twarda bramka: dopóki konto nie jest domknięte, kierujemy na uzupełnienie (i tam jest też
  // opcja „nie akceptuję — usuń konto"). Czekamy aż user się załaduje, by nie migać redirectem.
  if (!isLoading && needsProfileCompletion(user) && !COMPLETION_ALLOWED_PATHS.includes(location.pathname)) {
    return <Navigate to="/uzupelnij-profil" replace />
  }

  return (
    <div className="min-h-screen flex flex-col">
      {!isHome && <Navbar />}
      <main className="flex-1">
        <div key={location.pathname}>
          <Outlet />
        </div>
      </main>
      <Footer />
    </div>
  )
}
