import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { EnrollmentModal } from './EnrollmentModal'
import { MemoryRouter } from 'react-router-dom'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => {
      const map: Record<string, string> = {
        'enroll.title': 'Zapis',
        'enroll.firstName': 'Imię',
        'enroll.lastName': 'Nazwisko',
        'enroll.email': 'Email',
        'enroll.phone': 'Telefon',
        'enroll.note': 'Notatka',
        'enroll.optional': 'opcjonalne',
        'enroll.submit': 'Zapisz się',
        'enroll.firstNameRequired': 'Imię jest wymagane',
        'enroll.lastNameRequired': 'Nazwisko jest wymagane',
        'enroll.nameMinLength': 'Minimum 3 znaki',
        'enroll.emailRequired': 'Email jest wymagany',
        'enroll.emailInvalid': 'Nieprawidłowy email',
        'enroll.phoneRequired': 'Telefon jest wymagany',
        'enroll.phoneInvalid': 'Nieprawidłowy numer',
        'enroll.privacyRequired': 'Zgoda wymagana',
        'enroll.privacyLabel': 'Akceptuję',
        'enroll.privacyLink': 'politykę prywatności',
        'enroll.success': 'Zapisano!',
      }
      return map[key] ?? key
    },
  }),
}))

vi.mock('../../api/public', () => ({
  publicApi: {
    enroll: vi.fn(),
  },
}))

function renderModal(props?: Partial<Parameters<typeof EnrollmentModal>[0]>) {
  return render(
    <MemoryRouter>
      <EnrollmentModal
        isOpen={true}
        onClose={vi.fn()}
        eventId="test-id"
        eventName="Trening personalny"
        {...props}
      />
    </MemoryRouter>
  )
}

function getInput(label: string): HTMLInputElement {
  const allInputs = document.querySelectorAll('input[type="text"], input[type="email"], input[type="tel"]')
  for (const input of allInputs) {
    const parent = input.closest('div')
    if (parent?.querySelector('label')?.textContent?.includes(label)) {
      return input as HTMLInputElement
    }
  }
  throw new Error(`Input for "${label}" not found`)
}

describe('EnrollmentModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should render form when open', () => {
    renderModal()

    expect(screen.getByText(/Imię/)).toBeInTheDocument()
    expect(screen.getByText(/Nazwisko/)).toBeInTheDocument()
    expect(screen.getByText(/Email/)).toBeInTheDocument()
    expect(screen.getByText(/Telefon/)).toBeInTheDocument()
    expect(screen.getByText('Zapisz się')).toBeInTheDocument()
  })

  it('should not render when closed', () => {
    renderModal({ isOpen: false })
    expect(screen.queryByText('Zapisz się')).not.toBeInTheDocument()
  })

  it('should show validation errors on empty submit', async () => {
    const user = userEvent.setup()
    renderModal()

    await user.click(screen.getByText('Zapisz się'))

    expect(screen.getByText('Imię jest wymagane')).toBeInTheDocument()
    expect(screen.getByText('Nazwisko jest wymagane')).toBeInTheDocument()
    expect(screen.getByText('Email jest wymagany')).toBeInTheDocument()
    expect(screen.getByText('Telefon jest wymagany')).toBeInTheDocument()
    expect(screen.getByText('Zgoda wymagana')).toBeInTheDocument()
  })

  it('should show error for short name on blur', async () => {
    const user = userEvent.setup()
    renderModal()

    const firstNameInput = getInput('Imię')
    await user.type(firstNameInput, 'Ab')
    await user.tab()

    expect(screen.getByText('Minimum 3 znaki')).toBeInTheDocument()
  })

  it('should show error for invalid email on blur', async () => {
    const user = userEvent.setup()
    renderModal()

    const emailInput = getInput('Email')
    await user.type(emailInput, 'not-an-email')
    await user.tab()

    expect(screen.getByText('Nieprawidłowy email')).toBeInTheDocument()
  })

  it('should show error for invalid phone on blur', async () => {
    const user = userEvent.setup()
    renderModal()

    const phoneInput = getInput('Telefon')
    await user.type(phoneInput, 'abc')
    await user.tab()

    expect(screen.getByText('Nieprawidłowy numer')).toBeInTheDocument()
  })

  it('should submit form with valid data', async () => {
    const { publicApi } = await import('../../api/public')
    vi.mocked(publicApi.enroll).mockResolvedValue(undefined as never)

    const user = userEvent.setup()
    renderModal()

    await user.type(getInput('Imię'), 'Anna')
    await user.type(getInput('Nazwisko'), 'Nowak')
    await user.type(getInput('Email'), 'anna@test.com')
    await user.type(getInput('Telefon'), '123456789')
    await user.click(screen.getByRole('checkbox'))
    await user.click(screen.getByText('Zapisz się'))

    expect(publicApi.enroll).toHaveBeenCalledWith('test-id', expect.objectContaining({
      firstName: 'Anna',
      lastName: 'Nowak',
      email: 'anna@test.com',
      phone: '123456789',
    }))
  })

  it('should show success after enrollment', async () => {
    const { publicApi } = await import('../../api/public')
    vi.mocked(publicApi.enroll).mockResolvedValue(undefined as never)

    const user = userEvent.setup()
    renderModal()

    await user.type(getInput('Imię'), 'Anna')
    await user.type(getInput('Nazwisko'), 'Nowak')
    await user.type(getInput('Email'), 'anna@test.com')
    await user.type(getInput('Telefon'), '123456789')
    await user.click(screen.getByRole('checkbox'))
    await user.click(screen.getByText('Zapisz się'))

    expect(await screen.findByText('Zapisano!')).toBeInTheDocument()
  })

  it('should show server error on failure', async () => {
    const { publicApi } = await import('../../api/public')
    vi.mocked(publicApi.enroll).mockRejectedValue(new Error('Brak miejsc'))

    const user = userEvent.setup()
    renderModal()

    await user.type(getInput('Imię'), 'Anna')
    await user.type(getInput('Nazwisko'), 'Nowak')
    await user.type(getInput('Email'), 'anna@test.com')
    await user.type(getInput('Telefon'), '123456789')
    await user.click(screen.getByRole('checkbox'))
    await user.click(screen.getByText('Zapisz się'))

    expect(await screen.findByText('Brak miejsc')).toBeInTheDocument()
  })
})
