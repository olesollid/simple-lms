import type { CourseResponse } from '../types/course.ts'
import { ApiError, ApiResponseError } from '../services/apiClient.ts'

export type CourseListState =
  | { status: 'loading' }
  | { status: 'success'; courses: CourseResponse[] }
  | { status: 'error'; message: string }

export function courseListError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return 'Please sign in to view courses, then try again.'
    if (error.status === 403) return 'You do not have permission to view these courses.'
    return error.message
  }
  if (error instanceof ApiResponseError) {
    return 'The server returned an unexpected response. Please try again.'
  }
  return 'Check your connection and try again.'
}
