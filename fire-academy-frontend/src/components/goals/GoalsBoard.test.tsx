import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { GoalsBoard } from './GoalsBoard'
import { adminApi } from '../../api/admin'
import { todayIso, addDaysIso } from '../../utils/calendarRange'
import type { AthleteGoal } from '../../types'

const tMap: Record<string, string> = {
  'goals.title': 'Cele',
  'goals.add': 'Ustaw cel',
  'goals.addNext': 'Ustaw nowy cel',
  'goals.addTitle': 'Nowy cel',
  'goals.reopen': 'Cofnij osiągnięcie',
  'goals.generalSection': 'Cele treningowe',
  'goals.weightSection': 'Cele wagowe',
  'goals.horizon.SHORT': 'Krótkoterminowy',
  'goals.horizon.MEDIUM': 'Średnioterminowy',
  'goals.horizon.LONG': 'Długoterminowy',
}

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, vars?: Record<string, unknown>) =>
      tMap[key] ?? (vars ? `${key} ${JSON.stringify(vars)}` : key),
  }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

vi.mock('../../api/admin', () => ({
  adminApi: {
    getAthleteGoals: vi.fn(),
    getAthleteWeights: vi.fn(),
    deleteAthleteGoal: vi.fn(),
    reopenAthleteGoal: vi.fn(),
  },
}))

function goal(over: Partial<AthleteGoal> = {}): AthleteGoal {
  return {
    id: 'g1', kind: 'GENERAL', horizon: 'SHORT', content: 'Podciągnięcie x10',
    targetDate: null, achievedAt: null, achievedAutomatically: false,
    targetWeightKg: null, startWeightKg: null,
    ...over,
  }
}

function renderBoard() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <GoalsBoard athleteId="a1" />
    </QueryClientProvider>,
  )
}

describe('GoalsBoard', () => {
  beforeEach(() => {
    vi.mocked(adminApi.getAthleteWeights).mockResolvedValue({
      points: [], currentTrendKg: null, weeklyChangePercent: null,
      trendReadings: 0, minReadingsToCloseGoal: 3,
      lowestTrendKg: null, lowestTrendDate: null, lowestTrendWindowDays: 90,
    })
  })

  it('shouldOfferANewGoalWhileTheAchievedOneIsStillCelebrating', async () => {
    // Closing every goal at once must not leave the coach with six trophies and nowhere to write
    // the next thing until the celebration window runs out.
    vi.mocked(adminApi.getAthleteGoals).mockResolvedValue({
      active: [],
      achieved: [goal({ achievedAt: addDaysIso(todayIso(), -1) })],
    })
    renderBoard()

    await userEvent.click((await screen.findAllByRole('button', { name: 'Ustaw nowy cel' }))[0])

    expect(await screen.findByRole('dialog', { name: 'Nowy cel' })).toBeInTheDocument()
  })

  it('shouldOfferReopenOnlyForAnAutomaticAchievement', async () => {
    vi.mocked(adminApi.getAthleteGoals).mockResolvedValue({
      active: [],
      achieved: [
        goal({ achievedAt: todayIso(), achievedAutomatically: false }),
        goal({
          id: 'g2', kind: 'WEIGHT', horizon: 'MEDIUM', content: 'Zejść do 73 kg',
          achievedAt: todayIso(), achievedAutomatically: true,
          targetWeightKg: 73, startWeightKg: 78,
        }),
      ],
    })
    renderBoard()

    // Two trophies on the board, one reopen button — the coach's own decision stays final
    expect(await screen.findAllByRole('button', { name: 'Ustaw nowy cel' })).toHaveLength(2)
    expect(screen.getAllByRole('button', { name: /Cofnij osiągnięcie/ })).toHaveLength(1)
  })
})
