import type { EnrollmentRequest, EnrollmentResponse, Role } from '../types/enrollment.ts'
import type { ApiCallOptions, ApiRequest } from './apiClient.ts'

export function createEnrollmentService(request: ApiRequest) {
  const path = (courseId: number) => `/courses/${courseId}/enrollments`
  const memberPath = (courseId: number, email: string) =>
    `${path(courseId)}/${encodeURIComponent(email)}`
  return {
    getEnrollments: (courseId: number, role?: Role, options?: ApiCallOptions) =>
      request<EnrollmentResponse[]>(`${path(courseId)}${role ? `?role=${role}` : ''}`, options),
    getEnrollment: (courseId: number, email: string, options?: ApiCallOptions) =>
      request<EnrollmentResponse>(memberPath(courseId, email), options),
    enroll: (courseId: number, body: EnrollmentRequest, options?: ApiCallOptions) =>
      request<EnrollmentResponse>(path(courseId), { ...options, method: 'POST', body }),
    changeRole: (courseId: number, email: string, role: Role, options?: ApiCallOptions) =>
      request<EnrollmentResponse>(memberPath(courseId, email), {
        ...options, method: 'PUT', body: { email, role },
      }),
    unenroll: (courseId: number, email: string, options?: ApiCallOptions) =>
      request<void>(memberPath(courseId, email), { ...options, method: 'DELETE' }),
  }
}
