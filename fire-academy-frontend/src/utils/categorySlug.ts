import type { EventCategory } from '../types'

const SLUG_TO_CATEGORY: Record<string, EventCategory> = {
  treningi: 'TRAINING',
  obozy: 'CAMP',
  szkolenia: 'COURSE',
}

const CATEGORY_TO_SLUG: Record<EventCategory, string> = {
  TRAINING: 'treningi',
  CAMP: 'obozy',
  COURSE: 'szkolenia',
}

export function slugToCategory(slug: string): EventCategory | undefined {
  return SLUG_TO_CATEGORY[slug]
}

export function categoryToSlug(category: EventCategory): string {
  return CATEGORY_TO_SLUG[category]
}
