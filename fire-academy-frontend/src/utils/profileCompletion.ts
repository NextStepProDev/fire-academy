import type { User } from '../types'

export type ProfileField = 'firstName' | 'lastName' | 'phone'

// Pola profilu wymagane do zapisu na wydarzenie (organizator potrzebuje kontaktu).
// Dodanie nowego wymaganego pola w przyszłości = dopisanie go tutaj — formularz
// uzupełniania i bramki zapisu policzą braki automatycznie.
export const REQUIRED_PROFILE_FIELDS: ProfileField[] = ['firstName', 'lastName', 'phone']

/** Zwraca listę wymaganych pól profilu, których użytkownik jeszcze nie uzupełnił. */
export function getMissingProfileFields(
  user: Pick<User, 'firstName' | 'lastName' | 'phone'> | null | undefined
): ProfileField[] {
  if (!user) return []
  return REQUIRED_PROFILE_FIELDS.filter((field) => !(user[field] ?? '').trim())
}
