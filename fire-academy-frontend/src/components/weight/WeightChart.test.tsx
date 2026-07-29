import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { WeightChart } from './WeightChart'
import type { WeightPoint } from '../../types'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

function series(): WeightPoint[] {
  return [
    { date: '2027-06-01', weightKg: 80.0, trendKg: null },
    { date: '2027-06-08', weightKg: 79.4, trendKg: 79.6 },
    { date: '2027-06-15', weightKg: 78.9, trendKg: 79.1 },
  ]
}

describe('WeightChart', () => {
  it('draws the trend as a line and the readings as separate marks', () => {
    // The trend is the story; the daily readings are the noise behind it.
    const { container } = render(<WeightChart points={series()} />)

    const path = container.querySelector('path')
    expect(path).toBeInTheDocument()
    // 2px round-capped line, per the mark spec
    expect(path).toHaveAttribute('stroke-width', '2')
    expect(path).toHaveAttribute('stroke-linecap', 'round')
    // One faint dot per reading, plus the end marker on the trend
    expect(container.querySelectorAll('circle').length).toBe(series().length + 1)
  })

  it('skips readings that have no trend yet', () => {
    // The first week cannot have a 7-day average, and inventing one would draw a line through
    // data that does not exist.
    const { container } = render(<WeightChart points={series()} />)

    const d = container.querySelector('path')!.getAttribute('d')!
    // Two trend points -> one move plus one line segment
    expect(d.match(/[ML]/g)).toHaveLength(2)
  })

  it('labels only the current trend, never every point', () => {
    render(<WeightChart points={series()} />)

    expect(screen.getByText('79.1 kg')).toBeInTheDocument()
    expect(screen.queryByText('79.6 kg')).toBeNull()
    expect(screen.queryByText('78.9 kg')).toBeNull()
  })

  it('offers a table so no value is reachable only by hovering', () => {
    const user = userEvent.setup()
    render(<WeightChart points={series()} />)

    expect(screen.queryByRole('table')).toBeNull()
    return user.click(screen.getByRole('button', { name: 'weight.showTable' })).then(() => {
      const rows = screen.getAllByRole('row')
      // header + one row per reading
      expect(rows).toHaveLength(series().length + 1)
      expect(screen.getByText('80.0')).toBeInTheDocument()
    })
  })

  it('names both marks so identity never rests on colour alone', () => {
    render(<WeightChart points={series()} />)

    expect(screen.getByText('weight.legendTrend')).toBeInTheDocument()
    expect(screen.getByText('weight.legendDaily')).toBeInTheDocument()
  })

  it('renders nothing at all without readings', () => {
    const { container } = render(<WeightChart points={[]} />)
    expect(container).toBeEmptyDOMElement()
  })

  it('spaces points by the calendar, not by their order', () => {
    // A fortnight's gap has to look like a gap — even spacing would redraw a break in logging as
    // continuous progress.
    const { container } = render(<WeightChart points={[
      { date: '2027-06-01', weightKg: 80, trendKg: 80 },
      { date: '2027-06-02', weightKg: 80, trendKg: 80 },
      { date: '2027-06-30', weightKg: 78, trendKg: 78 },
    ]} />)

    const xs = [...container.querySelectorAll('circle')]
      .slice(0, 3)
      .map(c => Number(c.getAttribute('cx')))
    // First two sit next to each other; the third is far away
    expect(xs[1] - xs[0]).toBeLessThan((xs[2] - xs[1]) / 5)
  })
})
