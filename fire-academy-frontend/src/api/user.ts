import { fetchApi } from './client'
import type { MyTrainingEnrollment } from '../types'

interface EnrollTrainingRequest {
  startMonth: string
  months?: number
}

export const userApi = {
  getMyTrainingEnrollments: () =>
    fetchApi<MyTrainingEnrollment[]>('/user/training-enrollments'),

  enrollTrainingSlot: (slotId: string, data: EnrollTrainingRequest) =>
    fetchApi<{ message: string }>(`/user/training-slots/${slotId}/enroll`, {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  cancelTrainingEnrollment: (id: string) =>
    fetchApi<void>(`/user/training-enrollments/${id}`, { method: 'DELETE' }),
}
