import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AdminEvents } from './AdminEvents'
import type { EventInstance } from '../../types'

const tMap: Record<string, string> = {
  'events.title': 'Terminy',
  'events.editTitle': 'Edytuj termin',
  'events.createTitle': 'Nowy termin',
  'events.eventType': 'Rodzaj',
  'events.startDate': 'Data rozpoczęcia',
  'events.endDate': 'Data zakończenia',
  'events.location': 'Lokalizacja',
  'actions.create': 'Dodaj',
  'actions.cancel': 'Anuluj',
  'actions.save': 'Zapisz',
  'actions.edit': 'Edytuj',
}

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => tMap[key] ?? key }),
}))

vi.mock('../../api/admin', () => ({
  adminApi: {
    getEvents: vi.fn(),
    getEventTypes: vi.fn(),
    createEvent: vi.fn(),
    updateEvent: vi.fn(),
    deleteEvent: vi.fn(),
    toggleEventActive: vi.fn(),
  },
}))

vi.mock('../../context/ToastContext', () => ({
  useToast: () => ({ showToast: vi.fn() }),
}))

// The markers query would otherwise fire a real request; the notebook is not what this file is about.
vi.mock('../../components/notes/useNoteMarkers', () => ({
  useNoteMarkers: () => ({ notedSlotIds: new Set(), notedEventIds: new Set() }),
}))

vi.mock('../../components/notes/AdminPrivateNote', () => ({
  AdminPrivateNote: () => null,
}))

/** Starts far enough ahead that the list filter keeps it and the date guard is out of the picture. */
const upcoming: EventInstance = {
  id: 'ev1', eventTypeId: 'et1', eventTypeName: 'Obóz letni', description: null,
  startDate: '2099-08-20', endDate: '2099-08-27',
  startTime: null, endTime: null, location: 'Katowice',
  price: 1200, maxParticipants: 12, availableSpots: 12, active: true, enrollmentCount: 0,
  createdAt: '2026-01-01T00:00:00Z',
}

async function renderEvents() {
  const { adminApi } = await import('../../api/admin')
  vi.mocked(adminApi.getEvents).mockResolvedValue([upcoming])
  vi.mocked(adminApi.getEventTypes).mockResolvedValue([])
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <AdminEvents category="CAMP" />
    </QueryClientProvider>
  )
  return adminApi
}

describe('AdminEvents', () => {
  beforeEach(() => vi.clearAllMocks())

  /**
   * A refused save has to say so. Without this the modal simply sits there: the spinner stops, nothing
   * changes, and the only trace is an unhandled rejection nobody is looking at.
   */
  it('shows the server message when saving fails and keeps the form open', async () => {
    const user = userEvent.setup()
    const adminApi = await renderEvents()
    vi.mocked(adminApi.updateEvent).mockRejectedValue(
      new Error('Data rozpoczęcia nie może być wcześniejsza niż dzisiaj'))

    await user.click(await screen.findByRole('button', { name: 'Edytuj' }))
    // Any real edit will do — Save stays disabled until something actually differs.
    await user.type(screen.getByDisplayValue('1200'), '0')
    await user.click(screen.getByRole('button', { name: 'Zapisz' }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Data rozpoczęcia nie może być wcześniejsza niż dzisiaj')
    // Still open: the values are the user's only copy, and closing would throw them away.
    expect(screen.getByRole('button', { name: 'Zapisz' })).toBeInTheDocument()
  })

  it('clears a previous error when the form is reopened', async () => {
    const user = userEvent.setup()
    const adminApi = await renderEvents()
    vi.mocked(adminApi.updateEvent).mockRejectedValue(new Error('Coś poszło nie tak'))

    await user.click(await screen.findByRole('button', { name: 'Edytuj' }))
    // Any real edit will do — Save stays disabled until something actually differs.
    await user.type(screen.getByDisplayValue('1200'), '0')
    await user.click(screen.getByRole('button', { name: 'Zapisz' }))
    await screen.findByRole('alert')

    await user.click(screen.getByRole('button', { name: 'Anuluj' }))
    await user.click(screen.getByRole('button', { name: 'Dodaj' }))

    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument())
  })
})
