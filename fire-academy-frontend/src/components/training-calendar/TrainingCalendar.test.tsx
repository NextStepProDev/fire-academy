import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { TrainingCalendar } from './TrainingCalendar'
import type { TrainingCalendarAdapter } from './adapter'
import { todayIso, weekRange } from '../../utils/calendarRange'
import type { CalendarRange, DeletedTrainingNotice, PersonalTraining, RecurringSession } from '../../types'

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
  'day.open': 'Pokaż dzień',
  'day.empty': 'Nic tego dnia.',
  'clipboard.copy': 'Kopiuj',
  'clipboard.cutAction': 'Wytnij',
  'detail.edit': 'Edytuj',
  'detail.delete': 'Usuń',
  'detail.duplicate': 'Powtórz za tydzień',
  'detail.markDone': 'Oznacz jako wykonany',
  'detail.markTaskDone': 'Oznacz jako zrobione',
  'empty': 'Brak treningów w tym okresie.',
}

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => tMap[key] ?? key }),
  // The modal reaches ApiError in api/client, which pulls in src/i18n and initialises i18next.
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

function training(over: Partial<PersonalTraining> = {}): PersonalTraining {
  return {
    id: 't1', kind: 'TRAINING', date: TODAY, startTime: null, endTime: null,
    title: 'Siła', description: null, targetCalories: null, status: 'PLANNED',
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
  recurring: RecurringSession[] = [],
): TrainingCalendarAdapter {
  const week = weekRange(TODAY)
  const range: CalendarRange = { from: week.from, to: week.to, trainings, recurring, deletions }
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

/**
 * jsdom ships no matchMedia at all, so the calendar sees a desktop by default. Phone-sized tests
 * install one that answers true for the compact query and false for everything else.
 */
function mockCompactViewport() {
  vi.stubGlobal('matchMedia', (query: string) => ({
    matches: query.includes('max-width'),
    media: query,
    addEventListener: () => {},
    removeEventListener: () => {},
  }))
}

describe('TrainingCalendar', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.unstubAllGlobals()
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

  it('swaps the month view for a dot grid on a phone', async () => {
    // One column of 42 stacked cells is around 4400px of scrolling, most of it empty days. At phone
    // width the month becomes dots — the day's contents move behind a tap.
    mockCompactViewport()
    const user = userEvent.setup()
    renderCalendar(stubAdapter([training()]))
    await screen.findByRole('button', { name: 'Miesiąc' })

    await user.click(screen.getByRole('button', { name: 'Miesiąc' }))

    await waitFor(() => expect(screen.getAllByRole('button', { name: /^Pokaż dzień/ })).toHaveLength(42))
    // No per-cell add button: at 45px a day cell holds a dot, not a control
    expect(screen.queryByRole('button', { name: /^Dodaj trening/ })).toBeNull()
  })

  it('opens a day sheet with the full card behind a tap on the dot grid', async () => {
    mockCompactViewport()
    const user = userEvent.setup()
    renderCalendar(stubAdapter([training({ startTime: '17:00', endTime: '18:30' })]))
    await user.click(await screen.findByRole('button', { name: 'Miesiąc' }))

    await user.click(await screen.findByRole('button', { name: `Pokaż dzień ${TODAY}` }))

    const sheet = await screen.findByRole('dialog')
    expect(within(sheet).getByText('Siła')).toBeInTheDocument()
    expect(within(sheet).getByText('17:00–18:30')).toBeInTheDocument()
    expect(within(sheet).getByRole('button', { name: /Dodaj trening/ })).toBeInTheDocument()
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

  it('does not open a training when the click was meant to paste', async () => {
    // The whole day is a drop target while the clipboard is armed. Without this the click both
    // pastes AND opens the card underneath it, which is two actions from one tap.
    const user = userEvent.setup()
    const pasteTraining = vi.fn().mockResolvedValue(training())
    renderCalendar(stubAdapter([training()], { pasteTraining }))

    await user.click(await screen.findByRole('button', { name: 'Kopiuj' }))
    await user.click(screen.getByText('Siła'))

    await waitFor(() => expect(pasteTraining).toHaveBeenCalled())
    expect(screen.queryByText('Edytuj')).toBeNull()
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

  it('ticks off a task without asking how hard it was', async () => {
    // "How hard was staying under 2200 kcal, 1-10" is a question about nothing, and an answer would
    // land in the same RPE averages the coach reads training load from.
    const user = userEvent.setup()
    const completeTraining = vi.fn().mockResolvedValue(training())
    renderCalendar(stubAdapter([
      training({ kind: 'TASK', title: 'Limit kalorii', targetCalories: 2200 }),
    ], { role: 'athlete', completeTraining, uncompleteTraining: vi.fn() }))

    await user.click(await screen.findByText('Limit kalorii'))

    expect(await screen.findByText('Oznacz jako zrobione')).toBeInTheDocument()
    expect(screen.queryByText('detail.rpeQuestion')).toBeNull()

    await user.click(screen.getByText('Oznacz jako zrobione'))
    await waitFor(() => expect(completeTraining).toHaveBeenCalledWith('t1', { rpe: null, feedback: null }))
  })

  it('keeps a training and a task on the same day as two separate cards', async () => {
    // The whole point of a task being its own entry: the session can be nailed and the diet blown
    // on the same day, and one tick box could not report that.
    const user = userEvent.setup()
    const completeTraining = vi.fn().mockResolvedValue(training())
    renderCalendar(stubAdapter([
      training({ id: 'tr', title: 'Siła' }),
      training({ id: 'ts', kind: 'TASK', title: 'Limit kalorii', targetCalories: 2200 }),
    ], { role: 'athlete', completeTraining, uncompleteTraining: vi.fn() }))

    await screen.findByText('Siła')
    expect(screen.getByText(/2200 kcal/)).toBeInTheDocument()

    // Ticking off the task says nothing about the training standing next to it
    await user.click(screen.getByText('Limit kalorii'))
    await user.click(await screen.findByText('Oznacz jako zrobione'))

    await waitFor(() => expect(completeTraining).toHaveBeenCalledWith('ts', { rpe: null, feedback: null }))
    expect(completeTraining).toHaveBeenCalledTimes(1)
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

    resolveRange({ from: week.from, to: week.to, trainings: [training()], recurring: [], deletions: [] })

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

  it('keeps the grid standing while paging to a page that is not cached yet', async () => {
    // Tearing the grid down to a spinner collapses the page from six rows to nothing and snaps it
    // back one fetch later. It reads as a glitch, not as loading — and only on the first visit to a
    // month, which is exactly the kind of bug that gets dismissed as "it looked fine the second time".
    const user = userEvent.setup()
    const week = weekRange(TODAY)
    const first: CalendarRange = {
      from: week.from, to: week.to, trainings: [training()], recurring: [], deletions: [],
    }
    let resolveNext: (value: CalendarRange) => void = () => {}
    const fetchRange = vi.fn()
      .mockResolvedValueOnce(first)
      .mockReturnValueOnce(new Promise<CalendarRange>(resolve => { resolveNext = resolve }))
    renderCalendar(stubAdapter([], { fetchRange }))
    await screen.findByText('Siła')

    await user.click(screen.getByRole('button', { name: 'Następny okres' }))

    // Seven columns, still there, while the next week is in flight
    expect(screen.getAllByRole('button', { name: /^Dodaj trening/ })).toHaveLength(7)
    // The frame is kept, the contents are not: last week's session is not on next week's dates
    expect(screen.queryByText('Siła')).toBeNull()

    resolveNext({ ...first, trainings: [] })
  })

  it('does not keep one athlete\'s plan on screen under another name', async () => {
    // The other half of the same rule: paging is a different page of one entity, switching athlete is
    // a different entity. A spinner is the right answer there.
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const first = stubAdapter([training()])
    const second = stubAdapter([], {
      rangeKey: (from, to) => ['test', 'other-athlete', from, to],
      fetchRange: vi.fn().mockReturnValue(new Promise(() => {})),
    })
    const { rerender } = render(
      <QueryClientProvider client={queryClient}>
        <TrainingCalendar adapter={first} />
      </QueryClientProvider>
    )
    await screen.findByText('Siła')

    rerender(
      <QueryClientProvider client={queryClient}>
        <TrainingCalendar adapter={second} />
      </QueryClientProvider>
    )

    expect(screen.queryByText('Siła')).toBeNull()
  })

  it('shows the unread dot for a training the other side changed', async () => {
    renderCalendar(stubAdapter([training({ unread: true })]))

    expect(await screen.findByLabelText('unread.dot')).toBeInTheDocument()
  })

  it('shows group sessions with no controls on them', async () => {
    // A recurring session is computed from a subscription — it has no row to edit, so offering any
    // action on it would promise something the API cannot do.
    const user = userEvent.setup()
    renderCalendar(stubAdapter([], {}, [], [{
      date: TODAY, slotId: 's1', name: 'Kickboxing',
      instructorName: 'Jan Kowalski', startTime: '18:00', endTime: '19:00',
    }]))

    const tile = await screen.findByText('Kickboxing')
    expect(tile).toBeInTheDocument()
    expect(screen.getByText(/18:00–19:00/)).toBeInTheDocument()

    // No clipboard controls, and clicking it opens nothing
    expect(screen.queryByRole('button', { name: 'Kopiuj' })).toBeNull()
    await user.click(tile)
    expect(screen.queryByText('Edytuj')).toBeNull()
  })

  it('does not call the range empty when only group sessions are scheduled', async () => {
    renderCalendar(stubAdapter([], {}, [], [{
      date: TODAY, slotId: 's1', name: 'Kickboxing',
      instructorName: null, startTime: '18:00', endTime: null,
    }]))

    await screen.findByText('Kickboxing')
    expect(screen.queryByText('Brak treningów w tym okresie.')).toBeNull()
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
