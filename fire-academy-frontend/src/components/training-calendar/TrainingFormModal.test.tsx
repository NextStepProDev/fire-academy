import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { TrainingFormModal } from './TrainingFormModal'
import { ApiError } from '../../api/client'

const tMap: Record<string, string> = {
  'form.createTitle': 'Nowy trening',
  'form.editTitle': 'Edytuj trening',
  'form.title': 'Nazwa',
  'form.description': 'Opis',
  'form.setTime': 'Ustaw godzinę',
  'form.startTime': 'Od',
  'form.endTime': 'Do',
  'form.save': 'Zapisz',
  'form.cancel': 'Anuluj',
  'form.refresh': 'Odśwież i spróbuj ponownie',
}

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => tMap[key] ?? key }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

function renderForm(onSubmit: () => Promise<unknown>, onConflictRefresh = vi.fn()) {
  const onClose = vi.fn()
  render(
    <TrainingFormModal
      training={null}
      date="2027-03-10"
      onClose={onClose}
      onSubmit={onSubmit}
      onConflictRefresh={onConflictRefresh}
      canAttach={false}
    />
  )
  return { onClose, onConflictRefresh }
}

describe('TrainingFormModal', () => {
  beforeEach(() => vi.clearAllMocks())

  it('keeps the form open and shows the error when saving fails', async () => {
    // The reference implementation collapsed the form the moment Save was clicked, before the server
    // answered. On a failed write the training stayed unsaved while the user believed it was done.
    const user = userEvent.setup()
    const onSubmit = vi.fn().mockRejectedValue(new Error('Baza niedostępna'))
    const { onClose } = renderForm(onSubmit)

    await user.type(screen.getByLabelText('Nazwa'), 'Siła')
    await user.click(screen.getByRole('button', { name: 'Zapisz' }))

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Baza niedostępna'))
    expect(onClose).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'Zapisz' })).toBeInTheDocument()
  })

  it('closes only after the server confirms', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    const { onClose } = renderForm(onSubmit)

    await user.type(screen.getByLabelText('Nazwa'), 'Siła')
    await user.click(screen.getByRole('button', { name: 'Zapisz' }))

    await waitFor(() => expect(onClose).toHaveBeenCalled())
  })

  it('offers a refresh only on a version conflict', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn().mockRejectedValue(
      new ApiError('Ten trening został w międzyczasie zmieniony.', 409, 'CONFLICT'))
    const { onConflictRefresh } = renderForm(onSubmit)

    await user.type(screen.getByLabelText('Nazwa'), 'Siła')
    await user.click(screen.getByRole('button', { name: 'Zapisz' }))

    await user.click(await screen.findByRole('button', { name: 'Odśwież i spróbuj ponownie' }))
    expect(onConflictRefresh).toHaveBeenCalled()
  })

  it('does not offer a refresh for an ordinary failure', async () => {
    // Only a 409 means the row moved under us — a refresh would be meaningless noise otherwise.
    const user = userEvent.setup()
    const onSubmit = vi.fn().mockRejectedValue(new ApiError('Za długi tytuł', 400, 'VALIDATION_ERROR'))
    renderForm(onSubmit)

    await user.type(screen.getByLabelText('Nazwa'), 'Siła')
    await user.click(screen.getByRole('button', { name: 'Zapisz' }))

    await screen.findByRole('alert')
    expect(screen.queryByRole('button', { name: 'Odśwież i spróbuj ponownie' })).toBeNull()
  })

  it('sends no times unless an hour was explicitly set', async () => {
    // Untimed is the default: the coach has to opt IN to a clock time, not out of one.
    const user = userEvent.setup()
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    renderForm(onSubmit)

    await user.type(screen.getByLabelText('Nazwa'), 'Siła')
    await user.click(screen.getByRole('button', { name: 'Zapisz' }))

    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({ startTime: null, endTime: null })))
  })

  it('sends the hour once the time checkbox is ticked', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    renderForm(onSubmit)

    await user.type(screen.getByLabelText('Nazwa'), 'Sparing')
    await user.click(screen.getByLabelText('Ustaw godzinę'))
    await user.click(screen.getByRole('button', { name: 'Zapisz' }))

    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({ startTime: '17:00' })))
  })

  it('refuses to submit without a title', async () => {
    const onSubmit = vi.fn()
    renderForm(onSubmit)

    expect(screen.getByRole('button', { name: 'Zapisz' })).toBeDisabled()
  })
})
