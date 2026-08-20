import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { WeightPanel } from './WeightPanel'
import { myTrainingApi } from '../../api/user'
import { adminApi } from '../../api/admin'
import { todayIso, addDaysIso } from '../../utils/calendarRange'
import type { WeightSeries } from '../../types'

const tMap: Record<string, string> = {
  'weight.title': 'Waga',
  'weight.todayLabel': 'Waga rano (kg)',
  'weight.dateLabel': 'Dzień pomiaru',
  'weight.save': 'Zapisz',
  'weight.emptyClient': 'Brak pomiarów.',
}

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, vars?: Record<string, unknown>) =>
      tMap[key] ?? (vars ? `${key} ${JSON.stringify(vars)}` : key),
  }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

vi.mock('../../api/user', () => ({
  myTrainingApi: { getWeights: vi.fn(), recordWeight: vi.fn(), deleteWeight: vi.fn() },
}))

vi.mock('../../api/admin', () => ({
  adminApi: { getAthleteWeights: vi.fn() },
}))

// The real chart draws an SVG; the table it renders is what these tests are about, so the stub
// keeps the delete affordance and nothing else.
vi.mock('./WeightChart', () => ({
  WeightChart: ({ points, onDelete }: {
    points: { date: string; weightKg: number }[]
    onDelete?: (p: { date: string; weightKg: number }) => void
  }) => (
    <ul>
      {points.map(p => (
        <li key={p.date}>
          {p.date}
          {onDelete && (
            <button type="button" onClick={() => onDelete(p)}>{`Usuń pomiar: ${p.date}`}</button>
          )}
        </li>
      ))}
    </ul>
  ),
}))

const YESTERDAY = addDaysIso(todayIso(), -1)

function series(over: Partial<WeightSeries> = {}): WeightSeries {
  return {
    points: [],
    currentTrendKg: null,
    weeklyChangePercent: null,
    trendReadings: 0,
    minReadingsToCloseGoal: 3,
    lowestTrendKg: null,
    lowestTrendDate: null,
    lowestTrendWindowDays: 90,
    ...over,
  }
}

function renderPanel(athleteId: string | null = null) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <WeightPanel athleteId={athleteId} />
    </QueryClientProvider>,
  )
}

describe('WeightPanel', () => {
  beforeEach(() => {
    vi.mocked(myTrainingApi.getWeights).mockResolvedValue(series())
    vi.mocked(adminApi.getAthleteWeights).mockResolvedValue(series())
    vi.mocked(myTrainingApi.recordWeight).mockResolvedValue({
      date: YESTERDAY, weightKg: 74.2, trendKg: null,
    })
  })

  it('shouldSendTheChosenDayWhenBackfillingAMissedWeighIn', async () => {
    renderPanel()
    const dateField = await screen.findByLabelText('Dzień pomiaru')

    await userEvent.clear(await screen.findByLabelText('Waga rano (kg)'))
    await userEvent.type(await screen.findByLabelText('Waga rano (kg)'), '74,2')
    await userEvent.clear(dateField)
    await userEvent.type(dateField, YESTERDAY)
    await userEvent.click(screen.getByRole('button', { name: 'Zapisz' }))

    await waitFor(() => expect(myTrainingApi.recordWeight)
      .toHaveBeenCalledWith({ weightKg: 74.2, date: YESTERDAY }))
  })

  it('shouldReturnTheDayToTodayAfterSaving', async () => {
    // A date left pointing at last Tuesday would overwrite the wrong day on the next weigh-in.
    renderPanel()
    const dateField = await screen.findByLabelText('Dzień pomiaru')

    await userEvent.type(await screen.findByLabelText('Waga rano (kg)'), '74,2')
    await userEvent.clear(dateField)
    await userEvent.type(dateField, YESTERDAY)
    await userEvent.click(screen.getByRole('button', { name: 'Zapisz' }))

    await waitFor(() => expect(dateField).toHaveValue(todayIso()))
  })

  it('shouldDeleteTheReadingFromTheRowThatWasClicked', async () => {
    // Overwriting fixes a wrong number; only deleting fixes a day that never happened.
    vi.mocked(myTrainingApi.getWeights).mockResolvedValue(series({
      points: [
        { date: YESTERDAY, weightKg: 74.2, trendKg: 74.2 },
        { date: todayIso(), weightKg: 74.0, trendKg: 74.1 },
      ],
    }))
    renderPanel()

    await userEvent.click(await screen.findByRole('button', { name: `Usuń pomiar: ${YESTERDAY}` }))
    await userEvent.click(screen.getByRole('button', { name: 'weight.delete' }))

    await waitFor(() => expect(myTrainingApi.deleteWeight).toHaveBeenCalledWith(YESTERDAY))
  })

  it('shouldNotOfferDeletionToTheCoach', async () => {
    // The coach reads the scale; nobody edits somebody else's weigh-ins.
    vi.mocked(adminApi.getAthleteWeights).mockResolvedValue(series({
      points: [{ date: YESTERDAY, weightKg: 74.2, trendKg: 74.2 }],
    }))
    renderPanel('athlete-1')

    // The row is there, so the absence below is about the button and not about an empty table
    expect(await screen.findByText(YESTERDAY)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Usuń pomiar/ })).not.toBeInTheDocument()
  })

  it('shouldAskTheServerForTheChosenRange', async () => {
    // Readings older than a quarter existed with nothing able to reach them. The window is a
    // server-side cap, so the page has to ask for the longer one rather than filter locally.
    renderPanel()
    await screen.findByRole('button', { name: 'weight.range.ALL' })

    await userEvent.click(screen.getByRole('button', { name: 'weight.range.ALL' }))

    await waitFor(() => expect(myTrainingApi.getWeights).toHaveBeenCalledWith('ALL'))
    expect(myTrainingApi.getWeights).toHaveBeenCalledWith('QUARTER')
  })

  it('shouldShowTheLowestConfirmedTrendWithTheDayItFellOn', async () => {
    // Not the lowest number the scale ever showed: the server sends the lowest CONFIRMED trend, the
    // same figure that could close a weight goal. The word "trend" and the window belong in the
    // label itself — somebody remembering a lower morning reading has to see why the two differ.
    vi.mocked(myTrainingApi.getWeights).mockResolvedValue(series({
      points: [{ date: todayIso(), weightKg: 70.4, trendKg: 70.9 }],
      currentTrendKg: 70.9,
      trendReadings: 5,
      lowestTrendKg: 69.8,
      lowestTrendDate: '2026-06-12',
      lowestTrendWindowDays: 90,
    }))
    renderPanel()

    // The label carries the months the server sent, not a number baked into the translation
    expect(await screen.findByText(/weight\.lowestTrend.*"months":3/)).toBeInTheDocument()
    expect(screen.getByText('69.8 kg')).toBeInTheDocument()
    expect(screen.getByText('12 cze 2026')).toBeInTheDocument()
    // And what the number stands on, so 69,8 does not read as a broken app next to a remembered 68,9
    expect(screen.getByText(/weight\.lowestTrendHint.*"count":3/)).toBeInTheDocument()
  })

  it('shouldDropTheWholeLineWhenNoWeekWasEverConfirmed', async () => {
    // No dash, no "no data" — an unconfirmed number under a label saying "trend" is the one lie
    // this panel exists to prevent. The client checks null and nothing else.
    vi.mocked(myTrainingApi.getWeights).mockResolvedValue(series({
      points: [{ date: todayIso(), weightKg: 70.4, trendKg: 70.4 }],
      currentTrendKg: 70.4,
      trendReadings: 1,
      lowestTrendKg: null,
      lowestTrendDate: null,
    }))
    renderPanel()

    // The panel rendered, so the absence below is about the line and not about an empty page
    expect(await screen.findByText('Waga')).toBeInTheDocument()
    expect(screen.queryByText(/weight\.lowestTrend/)).not.toBeInTheDocument()
  })

  it('shouldDefaultToTodayAndRefuseTheFuture', async () => {
    renderPanel()

    const dateField = await screen.findByLabelText('Dzień pomiaru')
    expect(dateField).toHaveValue(todayIso())
    expect(dateField).toHaveAttribute('max', todayIso())
  })
})
