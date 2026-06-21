import { useCallback } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { saveRedirectPath } from '../utils/redirect'

/**
 * Enrolling in an event requires an account. A guest who clicks "Sign up" is redirected
 * to login with the return path remembered (after logging in they return to where they were).
 * A logged-in user performs the passed action (opening the enrollment modal).
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
