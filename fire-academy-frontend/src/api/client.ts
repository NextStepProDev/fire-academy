import i18n from '../i18n'
import type { User, MyEnrollments } from '../types'
import {
  getAccessToken,
  getRefreshToken,
  isAccessTokenExpired,
  saveTokens,
  clearTokens,
} from '../utils/tokenStorage'
import { refreshTokens } from './auth'

const API_BASE = '/api'

let refreshPromise: Promise<string | null> | null = null

async function ensureValidToken(): Promise<string | null> {
  const accessToken = getAccessToken()
  if (!accessToken) return null

  if (!isAccessTokenExpired()) return accessToken

  if (refreshPromise) return refreshPromise

  refreshPromise = doRefresh()
  try {
    return await refreshPromise
  } finally {
    refreshPromise = null
  }
}

async function doRefresh(): Promise<string | null> {
  const refresh = getRefreshToken()
  if (!refresh) {
    clearTokens()
    return null
  }

  try {
    const tokens = await refreshTokens(refresh)
    saveTokens(tokens)
    return tokens.accessToken
  } catch {
    clearTokens()
    window.dispatchEvent(new CustomEvent('auth:session-expired'))
    return null
  }
}

export async function fetchApi<T>(
  endpoint: string,
  options?: RequestInit
): Promise<T> {
  const token = await ensureValidToken()

  const headers: Record<string, string> = {
    'Accept-Language': i18n.language,
    ...(options?.headers as Record<string, string>),
  }

  if (!(options?.body instanceof FormData)) {
    headers['Content-Type'] = headers['Content-Type'] ?? 'application/json'
  }

  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }

  let response: Response

  const doFetch = async (): Promise<Response> => {
    const ctrl = new AbortController()
    const tid = setTimeout(() => ctrl.abort(), 30000)
    try {
      const res = await fetch(`${API_BASE}${endpoint}`, {
        ...options,
        headers,
        signal: ctrl.signal,
      })
      clearTimeout(tid)
      return res
    } catch (err) {
      clearTimeout(tid)
      throw err
    }
  }

  try {
    response = await doFetch()
  } catch {
    console.warn(`[API] ${options?.method ?? 'GET'} ${endpoint} — network error, retrying in 1.5s…`)
    await new Promise(r => setTimeout(r, 1500))
    try {
      response = await doFetch()
    } catch {
      throw new Error(i18n.t('network', { ns: 'errors' }))
    }
  }

  if (response.status === 401 && token) {
    const newToken = await doRefresh()
    if (newToken) {
      headers['Authorization'] = `Bearer ${newToken}`
      try {
        response = await doFetch()
      } catch {
        throw new Error(i18n.t('network', { ns: 'errors' }))
      }
    } else {
      throw new Error(i18n.t('sessionExpired', { ns: 'errors' }))
    }
  }

  // Gateway errors (502/503/504) almost always mean the backend is restarting — most
  // often a deploy, during which it can be unreachable for ~2 min while the JVM boots.
  // Retry a few times with backoff so a redeploy degrades to a brief "updating" blip
  // (combined with React Query's own retries) instead of a hard error screen. A genuine
  // 500 (app error) gets a single quick retry — no point waiting on a real bug.
  if (response.status >= 500 && response.status < 600) {
    const isGateway = response.status === 502 || response.status === 503 || response.status === 504
    const backoffs = isGateway ? [1500, 3000, 5000] : [1000]
    for (const delay of backoffs) {
      console.warn(`[API] ${options?.method ?? 'GET'} ${endpoint} → ${response.status}, retrying in ${delay}ms…`)
      await new Promise(r => setTimeout(r, delay))
      const retryToken = await ensureValidToken()
      if (retryToken) headers['Authorization'] = `Bearer ${retryToken}`
      try {
        response = await doFetch()
      } catch {
        continue // network error mid-retry — keep trying remaining backoffs
      }
      if (!(response.status >= 500 && response.status < 600)) break
    }
  }

  if (!response.ok) {
    const body = await response.json().catch(() => null)
    const serverMessage = body?.message
    if (serverMessage) {
      throw new Error(serverMessage)
    }
    if (response.status === 500) {
      throw new Error(i18n.t('server', { ns: 'errors' }))
    }
    // 502/503/504 survived the retry loop above -> backend still down (likely a deploy).
    if (response.status === 502 || response.status === 503 || response.status === 504) {
      throw new Error(i18n.t('serviceUpdating', { ns: 'errors' }))
    }
    if (response.status === 404) {
      throw new Error(i18n.t('notFound', { ns: 'errors' }))
    }
    if (response.status === 403) {
      throw new Error(i18n.t('forbidden', { ns: 'errors' }))
    }
    throw new Error(i18n.t('generic', { status: response.status, ns: 'errors' }))
  }

  if (response.status === 204) {
    return undefined as T
  }

  return response.json()
}

export const authApi = {
  getCurrentUser: () => fetchApi<User>('/user/me'),
  logout: () => {
    const refreshToken = getRefreshToken()
    clearTokens()
    if (refreshToken) {
      fetch(`${API_BASE}/auth/logout`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      }).catch(() => {})
    }
  },
  changePassword: (currentPassword: string, newPassword: string) =>
    fetchApi<void>('/user/me/password', {
      method: 'PUT',
      body: JSON.stringify({ currentPassword, newPassword }),
    }),
  deleteAccount: (password: string | null) =>
    fetchApi<void>('/user/me', {
      method: 'DELETE',
      body: JSON.stringify({ password }),
    }),
  updateMarketing: (enabled: boolean) =>
    fetchApi<User>('/user/me/marketing', {
      method: 'PUT',
      body: JSON.stringify({ enabled }),
    }),
  submitConsents: (acceptedPrivacy: boolean, acceptedMarketing: boolean) =>
    fetchApi<User>('/user/me/consents', {
      method: 'POST',
      body: JSON.stringify({ acceptedPrivacy, acceptedMarketing }),
    }),
  updateLanguage: (language: string) =>
    fetchApi<void>('/user/me/language', {
      method: 'PUT',
      body: JSON.stringify({ language }),
    }),
  updateProfile: (firstName: string, lastName: string, phone: string) =>
    fetchApi<User>('/user/me', {
      method: 'PUT',
      body: JSON.stringify({ firstName, lastName, phone }),
    }),
  uploadAvatar: (file: Blob) => {
    const formData = new FormData()
    formData.append('file', file, 'avatar.jpg')
    return fetchApi<User>('/user/me/avatar', { method: 'POST', body: formData })
  },
  deleteAvatar: () => fetchApi<User>('/user/me/avatar', { method: 'DELETE' }),
  enroll: (eventId: string, note?: string) =>
    fetchApi<{ message: string }>('/user/enrollments', {
      method: 'POST',
      body: JSON.stringify({ eventId, note: note?.trim() || null }),
    }),
  getMyEnrollments: () => fetchApi<MyEnrollments>('/user/enrollments'),
  cancelEnrollment: (id: string) =>
    fetchApi<void>(`/user/enrollments/${id}`, { method: 'DELETE' }),
  logoutAllDevices: () =>
    fetchApi<void>('/user/me/logout-all', { method: 'POST' }),
}

export const userApi = authApi
