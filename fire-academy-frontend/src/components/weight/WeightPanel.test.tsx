import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { WeightPanel } from './WeightPanel'
import { myTrainingApi } from '../../api/user'
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
  myTrainingApi: { getWeights: vi.fn(), recordWeight: vi.fn() },
}))

vi.mock('./WeightChart', () => ({ WeightChart: () => null }))

const YESTERDAY = addDaysIso(todayIso(), -1)

function series(over: Partial<WeightSeries> = {}): WeightSeries {
  return {
    points: [],
    currentTrendKg: null,
    weeklyChangePercent: null,
    trendReadings: 0,
    minReadingsToCloseGoal: 3,
    ...over,
  }
}

function renderPanel() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <WeightPanel athleteId={null} />
    </QueryClientProvider>,
  )
}

describe('WeightPanel', () => {
  beforeEach(() => {
    vi.mocked(myTrainingApi.getWeights).mockResolvedValue(series())
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

  it('shouldDefaultToTodayAndRefuseTheFuture', async () => {
    renderPanel()

    const dateField = await screen.findByLabelText('Dzień pomiaru')
    expect(dateField).toHaveValue(todayIso())
    expect(dateField).toHaveAttribute('max', todayIso())
  })
})
