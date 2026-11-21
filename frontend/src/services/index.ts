import { API_BASE_URL, apiRequest } from './api.ts'
import { createAiTutorService } from './aiTutor.ts'
import { createApiClient } from './apiClient.ts'
import { createServices } from './createServices.ts'
import { createMockFetch } from './mocks/mockFetch.ts'

export const USE_MOCK_API = import.meta.env.VITE_USE_MOCK_API === 'true'

const request = USE_MOCK_API
  ? createApiClient('/api', createMockFetch())
  : apiRequest

export const { courses, users, enrollments, modules, contents, submissions, groups, quizzes } = createServices(request)
// Streaming needs raw fetch (the request client buffers whole bodies), so it gets the transport.
export const aiTutor = createAiTutorService(request, {
  baseUrl: USE_MOCK_API ? '/api' : API_BASE_URL,
  fetchImplementation: USE_MOCK_API ? createMockFetch() : undefined,
})

export { ApiError, ApiResponseError } from './apiClient.ts'
export { TutorStreamError } from './aiTutor.ts'
