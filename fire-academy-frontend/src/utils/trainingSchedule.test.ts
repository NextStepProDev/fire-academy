import { describe, it, expect, afterEach, vi } from 'vitest'
import { remainingOccurrences, holidaysForDay, addMonths, currentMonth, visibleMonths } from './trainingSchedule'

// Mirrors the backend's TrainingBillingService counting — a divergence here would silently show users
// a different price than they are billed.
describe('remainingOccurrences', () => {
  afterEach(() => vi.useRealTimers())

  it('counts all occurrences of a weekday in a non-current month', () => {
    // February 2027: Mondays 1, 8, 15, 22 = 4; March 2027 has 5.
    expect(remainingOccurrences(1, '2027-02')).toBe(4)
    expect(remainingOccurrences(1, '2027-03')).toBe(5)
  })

  it('maps ISO day 7 to Sunday', () => {
    // February 2027: Sundays 7, 14, 21, 28 = 4
    expect(remainingOccurrences(7, '2027-02')).toBe(4)
  })

  it('subtracts closed dates only when they land on the weekday', () => {
    expect(remainingOccurrences(1, '2027-02', ['2027-02-08'])).toBe(3)
    expect(remainingOccurrences(1, '2027-02', ['2027-02-09'])).toBe(4) // a Tuesday — irrelevant
  })

  it('counts from today (inclusive) in the current month', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2027, 1, 10)) // 2027-02-10
    // Remaining Mondays from Feb 10: 15 and 22.
    expect(remainingOccurrences(1, '2027-02')).toBe(2)
  })
})

describe('holidaysForDay', () => {
  it('keeps only the dates landing on the slot weekday', () => {
    const holidays = [{ date: '2027-02-08' }, { date: '2027-02-09' }, { date: '2027-02-15' }]
    expect(holidaysForDay(holidays, 1)).toEqual(['2027-02-08', '2027-02-15'])
    expect(holidaysForDay(holidays, 7)).toEqual([])
  })
})

describe('addMonths', () => {
  it('shifts within a year and across year boundaries', () => {
    expect(addMonths('2027-05', 1)).toBe('2027-06')
    expect(addMonths('2027-12', 1)).toBe('2028-01')
    expect(addMonths('2027-01', -1)).toBe('2026-12')
  })
})

describe('month windows', () => {
  afterEach(() => vi.useRealTimers())

  it('exposes the current month plus the bookable horizon', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2027, 10, 20)) // 2027-11-20
    expect(currentMonth()).toBe('2027-11')
    expect(visibleMonths()).toEqual(['2027-11', '2027-12', '2028-01'])
  })
})
