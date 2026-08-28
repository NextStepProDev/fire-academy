import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useState } from 'react'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { TrainingEnrollModal } from './TrainingEnrollModal'
import type { TrainingSlotCard } from '../../types'

const tMap: Record<string, string> = {
  'enrollTraining.title': 'Zapis na trening',
  'enrollTraining.duration': 'Czas trwania',
  'enrollTraining.indefinite': 'Bezterminowo, do odwołania',
  'enrollTraining.fixed': 'Na czas określony',
  'enrollTraining.monthsCount': 'Liczba miesięcy',
  'enrollTraining.cancel': 'Anuluj',
  'enrollTraining.submit': 'Zapisz się',
  'enrollTraining.startMonth': 'Miesiąc startowy',
}

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => tMap[key] ?? key }),
}))

vi.mock('../../api/user', () => ({
  userApi: {
    getMyTrainingEnrollments: vi.fn().mockResolvedValue([]),
    enrollTrainingSlot: vi.fn(),
  },
}))

vi.mock('../../context/ToastContext', () => ({
  useToast: () => ({ showToast: vi.fn() }),
}))

const slot = (id: string, day: number): TrainingSlotCard => ({
  id, eventTypeId: 'et1', eventTypeName: 'Kickboxing',
  instructorId: null, instructorName: null,
  dayOfWeek: day, startTime: '18:00', endTime: '19:00',
  price: 100, maxParticipants: 6, availableSpots: 4, cancelledDates: [],
})

/** Mirrors how every page hosts this modal: rendered unconditionally, told what to show by a prop. */
function Host() {
  const [open, setOpen] = useState<TrainingSlotCard | null>(slot('mon', 1))
  return (
    <>
      <button onClick={() => setOpen(null)}>zamknij</button>
      <button onClick={() => setOpen(slot('wed', 3))}>otwórz środę</button>
      <button onClick={() => setOpen(slot('mon', 1))}>otwórz poniedziałek</button>
      <TrainingEnrollModal
        slot={open}
        startMonth="2099-09"
        holidays={[]}
        onClose={() => setOpen(null)}
      />
    </>
  )
}

describe('TrainingEnrollModal', () => {
  beforeEach(() => vi.clearAllMocks())

  /**
   * The commitment length must not travel between slots. Six months chosen for Monday and then left
   * standing on Wednesday is one click away from a subscription nobody meant to take on — and the
   * price line underneath only ever shows the first month, so nothing on screen contradicts it.
   */
  it('starts every slot at the open-ended default, whatever was picked for the previous one', async () => {
    const user = userEvent.setup()
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<QueryClientProvider client={queryClient}><Host /></QueryClientProvider>)

    const fixed = await screen.findByRole('radio', { name: 'Na czas określony' })
    await user.click(fixed)
    // Typed onto the default 1 — the field clamps to a minimum of 1, so clearing it first
    // would leave a 1 behind and the digit would land next to it anyway.
    const months = screen.getByLabelText('Liczba miesięcy')
    await user.type(months, '2')
    expect(months).toHaveValue(12)

    await user.click(screen.getByText('zamknij'))
    await user.click(screen.getByText('otwórz środę'))

    expect(await screen.findByRole('radio', { name: 'Bezterminowo, do odwołania' })).toBeChecked()
    expect(screen.getByRole('radio', { name: 'Na czas określony' })).not.toBeChecked()
    expect(screen.queryByLabelText('Liczba miesięcy')).not.toBeInTheDocument()
  })

  /**
   * Reopening the SAME slot keeps the same key, so nothing here rides on it: the reset comes from the
   * shell rendering nothing in between, which unmounts the form. Pinned because it is the half that
   * looks like it ought to leak.
   */
  it('also resets when the same slot is reopened', async () => {
    const user = userEvent.setup()
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<QueryClientProvider client={queryClient}><Host /></QueryClientProvider>)

    await user.click(await screen.findByRole('radio', { name: 'Na czas określony' }))
    await user.click(screen.getByText('zamknij'))
    await user.click(screen.getByText('otwórz poniedziałek'))

    expect(await screen.findByRole('radio', { name: 'Bezterminowo, do odwołania' })).toBeChecked()
  })
})
