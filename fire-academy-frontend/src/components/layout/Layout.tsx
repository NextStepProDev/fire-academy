import { Outlet, useLocation } from 'react-router-dom'
import { Navbar } from './Navbar'
import { Footer } from './Footer'

export function Layout() {
  const location = useLocation()
  const isHome = location.pathname === '/'

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
