import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AdminUserDetail } from './AdminUserDetail'
import type { AdminUserDetail as AdminUserDetailType, UserEnrollment } from '../../types'

const tMap: Record<string, string> = {
  'users.detail.back': 'Wróć do listy',
  'users.detail.currentTitle': 'Aktualne zapisy',
  'users.detail.archiveTitle': 'Archiwum',
  'users.detail.addToEvent': 'Dodaj do wydarzenia',
  'users.detail.removeEnrollmentTitle': 'Usuń zapis',
  'users.detail.removeEnrollmentConfirm': 'Usuń zapis',
  'users.detail.removeArchiveTitle': 'Usuń wpis z archiwum',
  'users.detail.removeArchiveConfirm': 'Usuń z archiwum',
  'users.detail.noCurrent': 'Brak aktualnych zapisów.',
  'users.detail.noArchive': 'Brak wpisów w archiwum.',
  'users.prevPage': 'Poprzednia',
  'users.nextPage': 'Następna',
}

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => tMap[key] ?? key }),
}))

vi.mock('../../api/admin', () => ({
  adminApi: {
    getUser: vi.fn(),
    getEvents: vi.fn(),
    deleteEnrollment: vi.fn(),
    adminEnroll: vi.fn(),
  },
}))

vi.mock('../../context/ToastContext', () => ({
  useToast: () => ({ showToast: vi.fn() }),
}))

const currentEnrollment: UserEnrollment = {
  id: 'en-current', eventId: 'ev1', eventName: 'Trening personalny', category: 'TRAINING',
  startDate: '2099-01-10', endDate: null, startTime: '18:00', endTime: '19:00',
  location: 'Katowice', note: null, addedByAdmin: false, past: false, createdAt: '2026-01-01T00:00:00Z',
}
const pastEnrollment: UserEnrollment = {
  id: 'en-past', eventId: 'ev2', eventName: 'Obóz zimowy', category: 'CAMP',
  startDate: '2020-01-10', endDate: '2020-01-15', startTime: null, endTime: null,
  location: 'Zakopane', note: null, addedByAdmin: true, past: true, createdAt: '2020-01-01T00:00:00Z',
}

const baseUser: AdminUserDetailType = {
  id: 'u1', email: 'jan@test.com', firstName: 'Jan', lastName: 'Kowalski', phone: '123456789',
  role: 'USER', isAdmin: false, superAdmin: false, emailVerified: true,
  marketingConsent: false, preferredLanguage: 'pl', hasPassword: true, oauthLinked: false, avatarUrl: null,
  createdAt: '2026-01-01T00:00:00Z', currentEnrollments: [], pastEnrollments: [],
}

async function renderDetail(overrides: Partial<AdminUserDetailType>) {
  const { adminApi } = await import('../../api/admin')
  vi.mocked(adminApi.getUser).mockResolvedValue({ ...baseUser, ...overrides })
  vi.mocked(adminApi.getEvents).mockResolvedValue([])
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <AdminUserDetail userId="u1" onBack={vi.fn()} />
    </QueryClientProvider>
  )
  return adminApi
}

describe('AdminUserDetail', () => {
  beforeEach(() => vi.clearAllMocks())

  it('renders profile with current and archived enrollments', async () => {
    const user = userEvent.setup()
    await renderDetail({ currentEnrollments: [currentEnrollment], pastEnrollments: [pastEnrollment] })
    expect(await screen.findByText('Jan Kowalski')).toBeInTheDocument()
    expect(screen.getByText('Trening personalny')).toBeInTheDocument()
    // Archiwum jest domyślnie zwinięte — rozwiń, zanim sprawdzisz zawartość.
    await user.click(screen.getByRole('button', { expanded: false, name: /Archiwum/ }))
    expect(screen.getByText('Obóz zimowy')).toBeInTheDocument()
  })

  it('removes an archived enrollment without notification', async () => {
    const user = userEvent.setup()
    const adminApi = await renderDetail({ currentEnrollments: [], pastEnrollments: [pastEnrollment] })
    await user.click(await screen.findByRole('button', { expanded: false, name: /Archiwum/ }))
    await screen.findByText('Obóz zimowy')

    await user.click(screen.getByRole('button', { name: 'Usuń wpis z archiwum' }))
    await user.click(screen.getByText('Usuń z archiwum'))

    await waitFor(() =>
      expect(adminApi.deleteEnrollment).toHaveBeenCalledWith('en-past', false)
    )
  })

  it('removes a current enrollment with notification', async () => {
    const user = userEvent.setup()
    const adminApi = await renderDetail({ currentEnrollments: [currentEnrollment], pastEnrollments: [] })
    await screen.findByText('Trening personalny')

    await user.click(screen.getByRole('button', { name: 'Usuń zapis' }))
    // dialog confirm button shares the same label "Usuń zapis"
    const confirmButtons = screen.getAllByRole('button', { name: 'Usuń zapis' })
    await user.click(confirmButtons[confirmButtons.length - 1])

    await waitFor(() =>
      expect(adminApi.deleteEnrollment).toHaveBeenCalledWith('en-current', true)
    )
  })

  it('opens the add-to-event modal', async () => {
    const user = userEvent.setup()
    await renderDetail({})
    await screen.findByText('Brak aktualnych zapisów.')

    await user.click(screen.getByText('Dodaj do wydarzenia'))

    // modal title (same label) now appears twice: trigger button + modal heading
    expect(screen.getAllByText('Dodaj do wydarzenia').length).toBeGreaterThan(1)
  })
})
