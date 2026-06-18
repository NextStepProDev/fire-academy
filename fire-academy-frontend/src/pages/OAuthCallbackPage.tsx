import { useEffect, useRef } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { consumeRedirectPath } from '../utils/redirect'
import { getMissingProfileFields } from '../utils/profileCompletion'
import { LoadingSpinner } from '../components/ui/LoadingSpinner'

export function OAuthCallbackPage() {
  const { loginWithTokens } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const processed = useRef(false)

  useEffect(() => {
    if (processed.current) return
    processed.current = true

    const accessToken = searchParams.get('accessToken')
    const refreshToken = searchParams.get('refreshToken')
    const expiresIn = searchParams.get('expiresIn')

    if (!accessToken || !refreshToken || !expiresIn) {
      navigate('/login', { replace: true })
      return
    }

    loginWithTokens({
      accessToken,
      refreshToken,
      expiresIn: Number(expiresIn),
    })
      .then((loggedInUser) => {
        // Brak wymaganych danych (np. telefon po rejestracji przez Google) → uzupełnij,
        // returnTo zostaje zapamiętany i jest użyty po zapisaniu danych.
        if (getMissingProfileFields(loggedInUser).length > 0) {
          navigate('/uzupelnij-profil', { replace: true })
        } else {
          navigate(consumeRedirectPath() || '/', { replace: true })
        }
      })
      .catch(() => {
        navigate('/login', { replace: true })
      })
  }, [searchParams, loginWithTokens, navigate])

  return (
    <div className="flex items-center justify-center min-h-[60vh]">
      <LoadingSpinner size="lg" />
    </div>
  )
}
