import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { TrainingCalendar } from './TrainingCalendar'
import type { TrainingCalendarAdapter } from './adapter'
import { todayIso, weekRange } from '../../utils/calendarRange'
import type { CalendarRange, DeletedTrainingNotice, PersonalTraining } from '../../types'

// The calendar always opens on the current week, so fixtures have to live there rather than on a
// fixed date — otherwise they render outside the visible range and nothing is found.
const TODAY = todayIso()

const tMap: Record<string, string> = {
  'nav.week': 'Tydzień',
  'nav.month': 'Miesiąc',
  'nav.today': 'Dziś',
  'nav.previous': 'Poprzedni okres',
  'nav.next': 'Następny okres',
  'day.add': 'Dodaj trening',
  'clipboard.copy': 'Kopiuj',
  'clipboard.cutAction': 'Wytnij',
  'detail.edit': 'Edytuj',
  'detail.delete': 'Usuń',
  'detail.duplicate': 'Powtórz za tydzień',
  'detail.markDone': 'Oznacz jako wykonany',
  'empty': 'Brak treningów w tym okresie.',
}

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => tMap[key] ?? key }),
  // The modal reaches ApiError in api/client, which pulls in src/i18n and initialises i18next.
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

function training(over: Partial<PersonalTraining> = {}): PersonalTraining {
  return {
    id: 't1', date: TODAY, startTime: null, endTime: null,
    title: 'Siła', description: null, status: 'PLANNED',
    completedAt: null, feedback: null, rpe: null,
    createdByAdmin: true, lastModifiedByAdmin: true,
    unread: false, commentCount: 0, attachments: [],
    version: 0, createdAt: '2027-03-01T10:00:00Z', updatedAt: '2027-03-01T10:00:00Z',
    ...over,
  }
}

function stubAdapter(
  trainings: PersonalTraining[],
  over: Partial<TrainingCalendarAdapter> = {},
  deletions: DeletedTrainingNotice[] = [],
): TrainingCalendarAdapter {
  const week = weekRange(TODAY)
  const range: CalendarRange = { from: week.from, to: week.to, trainings, deletions }
  return {
    role: 'coach',
    athleteId: 'a1',
    rangeKey: (from, to) => ['test', from, to],
    fetchRange: vi.fn().mockResolvedValue(range),
    createTraining: vi.fn(),
    updateTraining: vi.fn(),
    deleteTraining: vi.fn(),
    duplicateTraining: vi.fn(),
    pasteTraining: vi.fn(),
    getComments: vi.fn().mockResolvedValue([]),
    addComment: vi.fn(),
    markSeen: vi.fn().mockResolvedValue(undefined),
    dismissDeletions: vi.fn().mockResolvedValue(undefined),
    ...over,
  }
}

function renderCalendar(adapter: TrainingCalendarAdapter) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <TrainingCalendar adapter={adapter} />
    </QueryClientProvider>
  )
}

describe('TrainingCalendar', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
  })

  it('renders one column per day of the week', async () => {
    renderCalendar(stubAdapter([training()]))
    // One "add" button per day column
    await waitFor(() => expect(screen.getAllByRole('button', { name: /^Dodaj trening/ })).toHaveLength(7))
  })

  it('renders a fixed 42-cell grid in the month view', async () => {
    const user = userEvent.setup()
    renderCalendar(stubAdapter([]))
    await screen.findByRole('button', { name: 'Miesiąc' })

    await user.click(screen.getByRole('button', { name: 'Miesiąc' }))

    await waitFor(() => expect(screen.getAllByRole('button', { name: /^Dodaj trening/ })).toHaveLength(42))
  })

  it('has no hour axis anywhere', async () => {
    // The reference implementation positioned cards on a 07:00–23:00 pixel grid. This calendar is a
    // list of cards per day by design; an hour axis creeping back in would break that decision
    // silently, so assert its absence rather than trusting review.
    renderCalendar(stubAdapter([training({ startTime: '17:00', endTime: '18:30' })]))
    await screen.findByText('Siła')

    expect(screen.queryByText('07:00')).toBeNull()
    expect(screen.queryByText('23:00')).toBeNull()
    // The training's own hour is a label on the card, not an axis position
    expect(screen.getByText('17:00–18:30')).toBeInTheDocument()
  })

  it('places an untimed training above a timed one on the same day', async () => {
    // The API returns them in this order (untimed first, then by hour) and the grid must not resort.
    renderCalendar(stubAdapter([
      training({ id: 'untimed', title: 'Rozciąganie' }),
      training({ id: 'timed', title: 'Sparing', startTime: '17:00' }),
    ]))
    await screen.findByText('Rozciąganie')

    const titles = screen.getAllByText(/Rozciąganie|Sparing/).map(el => el.textContent)
    expect(titles).toEqual(['Rozciąganie', 'Sparing'])
  })

  it('shows no time label at all for an untimed training', async () => {
    // Not a dash, not "cały dzień" — no hour is the DEFAULT case, so a placeholder on every card
    // would be pure noise.
    renderCalendar(stubAdapter([training()]))
    const title = await screen.findByText('Siła')

    const card = title.closest('div')!
    expect(within(card).queryByText(/\d{2}:\d{2}/)).toBeNull()
  })

  it('keeps clipboard controls visible instead of hiding them behind hover', async () => {
    // Hover-only controls do not exist on a touch device, and a tap aimed at one opens the card
    // instead — the exact bug the reference implementation shipped.
    renderCalendar(stubAdapter([training()]))
    const copy = await screen.findByRole('button', { name: 'Kopiuj' })

    expect(copy.className).not.toMatch(/opacity-0/)
    // 24px minimum touch target (h-6 w-6 in Tailwind's 4px scale)
    expect(copy.className).toMatch(/\bh-6\b/)
    expect(copy.className).toMatch(/\bw-6\b/)
  })

  it('arms the clipboard on copy and pastes into the clicked day', async () => {
    const user = userEvent.setup()
    const pasteTraining = vi.fn().mockResolvedValue(training())
    renderCalendar(stubAdapter([training()], { pasteTraining }))

    await user.click(await screen.findByRole('button', { name: 'Kopiuj' }))
    // Every day becomes a paste target while the clipboard is armed
    const targets = await screen.findAllByText('clipboard.pasteHere')
    await user.click(targets[3])

    await waitFor(() => expect(pasteTraining).toHaveBeenCalledWith('t1', expect.any(String), 'COPY'))
  })

  it('offers no completion action when the adapter cannot complete', async () => {
    // The coach cannot tick off someone else's session; the missing adapter method IS the permission.
    const user = userEvent.setup()
    renderCalendar(stubAdapter([training()]))

    await user.click(await screen.findByText('Siła'))

    expect(await screen.findByText('Edytuj')).toBeInTheDocument()
    expect(screen.queryByText('Oznacz jako wykonany')).toBeNull()
  })

  it('offers completion when the adapter can complete', async () => {
    const user = userEvent.setup()
    renderCalendar(stubAdapter([training()], {
      role: 'athlete',
      completeTraining: vi.fn(),
      uncompleteTraining: vi.fn(),
    }))

    await user.click(await screen.findByText('Siła'))

    expect(await screen.findByText('Oznacz jako wykonany')).toBeInTheDocument()
  })

  it('waits for the page to actually arrive before marking it seen', async () => {
    // On a cached revisit React Query reports isSuccess in the same tick. Marking seen then would
    // clear the dots before the server had even counted them, so they would never be shown — the
    // exact bug that had to be fixed twice in the reference implementation.
    let resolveRange: (value: CalendarRange) => void = () => {}
    const pending = new Promise<CalendarRange>(resolve => { resolveRange = resolve })
    const markSeen = vi.fn().mockResolvedValue(undefined)
    const week = weekRange(TODAY)
    renderCalendar(stubAdapter([], { markSeen, fetchRange: vi.fn().mockReturnValue(pending) }))

    // While the range is still in flight nothing is marked
    expect(markSeen).not.toHaveBeenCalled()

    resolveRange({ from: week.from, to: week.to, trainings: [training()], deletions: [] })

    await screen.findByText('Siła')
    await waitFor(() => expect(markSeen).toHaveBeenCalledTimes(1))
  })

  it('marks seen exactly once per visit', async () => {
    // Paging weeks refetches the range; re-marking on every page would be pointless write traffic.
    const user = userEvent.setup()
    const markSeen = vi.fn().mockResolvedValue(undefined)
    renderCalendar(stubAdapter([training()], { markSeen }))
    await screen.findByText('Siła')
    await waitFor(() => expect(markSeen).toHaveBeenCalledTimes(1))

    await user.click(screen.getByRole('button', { name: 'Następny okres' }))
    await user.click(screen.getByRole('button', { name: 'Poprzedni okres' }))

    expect(markSeen).toHaveBeenCalledTimes(1)
  })

  it('shows the unread dot for a training the other side changed', async () => {
    renderCalendar(stubAdapter([training({ unread: true })]))

    expect(await screen.findByLabelText('unread.dot')).toBeInTheDocument()
  })

  it('announces deleted future trainings and lets them be dismissed', async () => {
    // A deleted training leaves nothing on the grid to notice, so the loss needs its own banner.
    const user = userEvent.setup()
    const dismissDeletions = vi.fn().mockResolvedValue(undefined)
    renderCalendar(stubAdapter([], { dismissDeletions }, [
      { id: 'd1', date: TODAY, startTime: null, title: 'Sparing', deletedAt: '2027-03-01T10:00:00Z' },
    ]))

    expect(await screen.findByText(/Sparing/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'deletions.dismiss' }))

    await waitFor(() => expect(dismissDeletions).toHaveBeenCalled())
  })
})
