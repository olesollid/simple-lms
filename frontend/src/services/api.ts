import { createApiClient } from './apiClient'

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL?.trim() || '/api'

export const apiRequest = createApiClient(API_BASE_URL)

export { ApiError, ApiResponseError } from './apiClient'
