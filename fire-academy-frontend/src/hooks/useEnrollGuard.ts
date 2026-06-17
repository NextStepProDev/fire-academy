import { useCallback } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { saveRedirectPath } from '../utils/redirect'

/**
 * Zapis na wydarzenie wymaga konta. Gość, który klika „Zapisz się", zostaje przekierowany
 * na logowanie z zapamiętaną ścieżką powrotu (po zalogowaniu wraca tam, gdzie był).
 * Zalogowany użytkownik wykonuje przekazaną akcję (otwarcie modala zapisu).
 */
export function useEnrollGuard() {
  const { isAuthenticated } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  return useCallback(
    (open: () => void) => {
      if (!isAuthenticated) {
        saveRedirectPath(location.pathname + location.search)
        navigate('/logowanie')
        return
      }
      open()
    },
    [isAuthenticated, navigate, location.pathname, location.search]
  )
}
