import { describe, it, expect, vi, afterEach } from 'vitest'
import { prefersReducedMotion } from './prefersReducedMotion'

const stub = (matches: boolean) =>
  vi.stubGlobal('matchMedia', (query: string) => ({
    matches: query.includes('prefers-reduced-motion') ? matches : false,
    media: query,
    addEventListener: () => {},
    removeEventListener: () => {},
  }))

describe('prefersReducedMotion', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('reports the preference when the system asks for less motion', () => {
    stub(true)
    expect(prefersReducedMotion()).toBe(true)
  })

  it('reports no preference when the system has none', () => {
    stub(false)
    expect(prefersReducedMotion()).toBe(false)
  })

  it('answers "no preference" where matchMedia does not exist', () => {
    // jsdom and the prerender pass both land here. Guessing "reduce" would strip the intro from
    // every prerendered first paint; guessing "no preference" matches an untouched browser.
    vi.stubGlobal('matchMedia', undefined)
    expect(prefersReducedMotion()).toBe(false)
  })
})
