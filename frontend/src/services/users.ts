import type { UserRequest, UserResponse } from '../types/user.ts'
import type { EnrollmentResponse } from '../types/enrollment.ts'
import type { ApiCallOptions, ApiRequest } from './apiClient.ts'

export function createUserService(request: ApiRequest) {
  const path = (email: string) => `/users/${encodeURIComponent(email)}`
  return {
    getCurrentUser: (options?: ApiCallOptions) => request<UserResponse>('/users/me', options),
    getUser: (email: string, options?: ApiCallOptions) =>
      request<UserResponse>(path(email), options),
    updateUser: (email: string, body: UserRequest, options?: ApiCallOptions) =>
      request<UserResponse>(path(email), { ...options, method: 'PUT', body }),
    deleteUser: (email: string, options?: ApiCallOptions) =>
      request<void>(path(email), { ...options, method: 'DELETE' }),
    getUserEnrollments: (email: string, options?: ApiCallOptions) =>
      request<EnrollmentResponse[]>(`${path(email)}/enrollments`, options),
  }
}
