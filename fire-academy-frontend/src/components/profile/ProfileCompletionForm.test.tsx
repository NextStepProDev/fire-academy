import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ProfileCompletionForm } from './ProfileCompletionForm'
import { getMissingProfileFields } from '../../utils/profileCompletion'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => {
      const map: Record<string, string> = {
        'profile.firstName': 'Imię',
        'profile.lastName': 'Nazwisko',
        'profile.phone': 'Telefon',
        'completion.submit': 'Zapisz i kontynuuj',
        'completion.errors.required': 'Uzupełnij wszystkie pola.',
        'completion.errors.nameTooShort': 'Imię i nazwisko muszą mieć co najmniej 3 znaki.',
      }
      return map[key] ?? key
    },
  }),
}))

const updateProfileMock = vi.fn()
vi.mock('../../api/client', () => ({
  authApi: { updateProfile: (...args: unknown[]) => updateProfileMock(...args) },
}))

const refreshUserMock = vi.fn()
let mockUser: { firstName: string; lastName: string; phone: string | null } | null = null
vi.mock('../../context/AuthContext', () => ({
  useAuth: () => ({ user: mockUser, refreshUser: refreshUserMock }),
}))

describe('getMissingProfileFields', () => {
  it('should report no missing fields when all required values are present', () => {
    expect(getMissingProfileFields({ firstName: 'Anna', lastName: 'Nowak', phone: '111' })).toEqual([])
  })

  it('should report blank and whitespace-only required fields as missing', () => {
    expect(getMissingProfileFields({ firstName: 'Anna', lastName: '', phone: '   ' })).toEqual(['lastName', 'phone'])
  })

  it('should report nothing for a null user', () => {
    expect(getMissingProfileFields(null)).toEqual([])
  })
})

describe('ProfileCompletionForm', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    updateProfileMock.mockResolvedValue(undefined)
  })

  it('should render only the missing fields', () => {
    mockUser = { firstName: 'Anna', lastName: 'Nowak', phone: '' }
    render(<ProfileCompletionForm />)
    expect(screen.getByText('Telefon')).toBeInTheDocument()
    expect(screen.queryByText('Imię')).not.toBeInTheDocument()
    expect(screen.queryByText('Nazwisko')).not.toBeInTheDocument()
  })

  it('should render nothing when the profile is already complete', () => {
    mockUser = { firstName: 'Anna', lastName: 'Nowak', phone: '111' }
    const { container } = render(<ProfileCompletionForm />)
    expect(container).toBeEmptyDOMElement()
  })

  it('should block submit and show an error when a missing field is left empty', async () => {
    mockUser = { firstName: 'Anna', lastName: 'Nowak', phone: '' }
    const user = userEvent.setup()
    render(<ProfileCompletionForm />)

    await user.click(screen.getByText('Zapisz i kontynuuj'))

    expect(screen.getByText('Uzupełnij wszystkie pola.')).toBeInTheDocument()
    expect(updateProfileMock).not.toHaveBeenCalled()
  })

  it('should reject a too-short name', async () => {
    mockUser = { firstName: 'Anna', lastName: '', phone: '111' }
    const user = userEvent.setup()
    const { container } = render(<ProfileCompletionForm />)

    const lastNameInput = container.querySelector<HTMLInputElement>('input[autocomplete="family-name"]')!
    await user.type(lastNameInput, 'Ab')
    await user.click(screen.getByText('Zapisz i kontynuuj'))

    expect(screen.getByText('Imię i nazwisko muszą mieć co najmniej 3 znaki.')).toBeInTheDocument()
    expect(updateProfileMock).not.toHaveBeenCalled()
  })

  it('should merge the filled field with existing account values and refresh the user', async () => {
    mockUser = { firstName: 'Anna', lastName: 'Nowak', phone: '' }
    const user = userEvent.setup()
    const { container } = render(<ProfileCompletionForm />)

    const phoneInput = container.querySelector<HTMLInputElement>('input[autocomplete="tel"]')!
    await user.type(phoneInput, '111222333')
    await user.click(screen.getByText('Zapisz i kontynuuj'))

    await waitFor(() => expect(updateProfileMock).toHaveBeenCalledWith('Anna', 'Nowak', '111222333'))
    expect(refreshUserMock).toHaveBeenCalled()
  })

  it('should surface a server error and not refresh on failure', async () => {
    mockUser = { firstName: 'Anna', lastName: 'Nowak', phone: '' }
    updateProfileMock.mockRejectedValue(new Error('Numer zajęty'))
    const user = userEvent.setup()
    const { container } = render(<ProfileCompletionForm />)

    const phoneInput = container.querySelector<HTMLInputElement>('input[autocomplete="tel"]')!
    await user.type(phoneInput, '111222333')
    await user.click(screen.getByText('Zapisz i kontynuuj'))

    expect(await screen.findByText('Numer zajęty')).toBeInTheDocument()
    expect(refreshUserMock).not.toHaveBeenCalled()
  })
})
