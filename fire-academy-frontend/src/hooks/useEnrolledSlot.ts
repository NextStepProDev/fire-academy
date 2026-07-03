import { useQuery } from '@tanstack/react-query'
import { useAuth } from '../context/AuthContext'
import { userApi } from '../api/user'

/**
 * Predicate for "the logged-in user is already subscribed to this slot" — used to show an
 * "already enrolled" state instead of an enroll button. Mirrors the backend collision rule
 * (`existsActiveFor`): a subscription blocks re-enrolling for `month` when it has no end or ends
 * on/after that month. Returns () => false for guests.
 */
export function useEnrolledSlot(): (slotId: string, month: string) => boolean {
  const { isAuthenticated } = useAuth()
  const { data } = useQuery({
    queryKey: ['user', 'training-enrollments'],
    queryFn: userApi.getMyTrainingEnrollments,
    enabled: isAuthenticated,
  })
  const enrollments = data ?? []
  return (slotId, month) =>
    enrollments.some(e => e.slotId === slotId && (e.endMonth === null || e.endMonth >= month))
}
