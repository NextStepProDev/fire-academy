import { describe, it, expect } from 'vitest'
import { slugToCategory, categoryToSlug } from './categorySlug'

describe('categorySlug', () => {
  describe('slugToCategory', () => {
    it('should map treningi to TRAINING', () => {
      expect(slugToCategory('treningi')).toBe('TRAINING')
    })

    it('should map obozy to CAMP', () => {
      expect(slugToCategory('obozy')).toBe('CAMP')
    })

    it('should map szkolenia to COURSE', () => {
      expect(slugToCategory('szkolenia')).toBe('COURSE')
    })

    it('should return undefined for unknown slug', () => {
      expect(slugToCategory('unknown')).toBeUndefined()
    })
  })

  describe('categoryToSlug', () => {
    it('should map TRAINING to treningi', () => {
      expect(categoryToSlug('TRAINING')).toBe('treningi')
    })

    it('should map CAMP to obozy', () => {
      expect(categoryToSlug('CAMP')).toBe('obozy')
    })

    it('should map COURSE to szkolenia', () => {
      expect(categoryToSlug('COURSE')).toBe('szkolenia')
    })
  })
})
