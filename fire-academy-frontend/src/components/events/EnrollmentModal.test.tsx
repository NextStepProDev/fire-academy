import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { EnrollmentModal } from './EnrollmentModal'
import { MemoryRouter } from 'react-router-dom'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => {
      const map: Record<string, string> = {
        'enroll.title': 'Potwierdź zapis',
        'enroll.intro': 'Zapisujesz się z danych konta:',
        'enroll.name': 'Imię i nazwisko',
        'enroll.email': 'Email',
        'enroll.phone': 'Telefon',
        'enroll.note': 'Notatka',
        'enroll.optional': 'opcjonalne',
        'enroll.submit': 'Zapisz się',
        'enroll.success': 'Zapisano!',
        'enroll.phoneMissingTitle': 'Uzupełnij telefon',
        'enroll.phoneMissingText': 'Dodaj numer telefonu',
        'enroll.phoneMissingCta': 'Przejdź do ustawień',
      }
      return map[key] ?? key
    },
  }),
}))

const enrollMock = vi.fn()
vi.mock('../../api/client', () => ({
  userApi: { enroll: (...args: unknown[]) => enrollMock(...args) },
}))

let mockUser: { firstName: string; lastName: string; email: string; phone: string | null } | null = {
  firstName: 'Anna',
  lastName: 'Nowak',
  email: 'anna@test.com',
  phone: '123456789',
}
vi.mock('../../context/AuthContext', () => ({
  useAuth: () => ({ user: mockUser }),
}))

function renderModal(props?: Partial<Parameters<typeof EnrollmentModal>[0]>) {
  return render(
    <MemoryRouter>
      <EnrollmentModal
        isOpen={true}
        onClose={vi.fn()}
        eventId="test-id"
        eventName="Obóz letni"
        {...props}
      />
    </MemoryRouter>
  )
}

describe('EnrollmentModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockUser = { firstName: 'Anna', lastName: 'Nowak', email: 'anna@test.com', phone: '123456789' }
  })

  it('should render confirmation with account data when open', () => {
    renderModal()
    expect(screen.getByText('Anna Nowak')).toBeInTheDocument()
    expect(screen.getByText('anna@test.com')).toBeInTheDocument()
    expect(screen.getByText('123456789')).toBeInTheDocument()
    expect(screen.getByText('Zapisz się')).toBeInTheDocument()
  })

  it('should not render when closed', () => {
    renderModal({ isOpen: false })
    expect(screen.queryByText('Zapisz się')).not.toBeInTheDocument()
  })

  it('should block submit and prompt for phone when account has no phone', () => {
    mockUser = { firstName: 'Piotr', lastName: 'Bez', email: 'piotr@test.com', phone: null }
    renderModal()
    expect(screen.getByText('Uzupełnij telefon')).toBeInTheDocument()
    expect(screen.getByText('Przejdź do ustawień')).toBeInTheDocument()
    expect(screen.queryByText('Zapisz się')).not.toBeInTheDocument()
  })

  it('should submit eventId and note', async () => {
    enrollMock.mockResolvedValue(undefined)
    const user = userEvent.setup()
    renderModal()

    await user.type(screen.getByRole('textbox'), 'Proszę o info')
    await user.click(screen.getByText('Zapisz się'))

    expect(enrollMock).toHaveBeenCalledWith('test-id', 'Proszę o info')
  })

  it('should show success after enrollment', async () => {
    enrollMock.mockResolvedValue(undefined)
    const user = userEvent.setup()
    renderModal()

    await user.click(screen.getByText('Zapisz się'))

    expect(await screen.findByText('Zapisano!')).toBeInTheDocument()
  })

  it('should show server error on failure', async () => {
    enrollMock.mockRejectedValue(new Error('Brak miejsc'))
    const user = userEvent.setup()
    renderModal()

    await user.click(screen.getByText('Zapisz się'))

    expect(await screen.findByText('Brak miejsc')).toBeInTheDocument()
  })
})
