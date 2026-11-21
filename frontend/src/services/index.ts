import { apiRequest } from './api.ts'
import { createApiClient } from './apiClient.ts'
import { createServices } from './createServices.ts'
import { createMockFetch } from './mocks/mockFetch.ts'

export const USE_MOCK_API = import.meta.env.VITE_USE_MOCK_API === 'true'

const request = USE_MOCK_API
  ? createApiClient('/api', createMockFetch())
  : apiRequest

export const { courses, users, enrollments, modules, contents, submissions, groups, quizzes } = createServices(request)
export { ApiError, ApiResponseError } from './apiClient.ts'
