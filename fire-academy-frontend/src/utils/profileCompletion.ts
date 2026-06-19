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

// Konta Google trafiają tu też po to, by domknąć zgodę na politykę prywatności (RODO) —
// rejestracja email/hasło wymusza ją od razu, OAuth nie. Ekran uzupełniania pokazujemy więc,
// gdy brakuje wymaganych pól LUB nie udzielono jeszcze zgody na politykę prywatności.
export function needsProfileCompletion(
  user: Pick<User, 'firstName' | 'lastName' | 'phone' | 'privacyAccepted'> | null | undefined
): boolean {
  if (!user) return false
  return getMissingProfileFields(user).length > 0 || !user.privacyAccepted
}
