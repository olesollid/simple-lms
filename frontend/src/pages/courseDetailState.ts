import type { CourseResponse } from '../types/course.ts'
import type { ModuleResponse } from '../types/module.ts'
import { ApiError, ApiResponseError } from '../services/apiClient.ts'

export type CourseDetailState =
  | { status: 'loading' }
  | {
      status: 'success'
      course: CourseResponse
      modules: ModuleResponse[]
      isTeacher: boolean
      /** Content the student has completed in this course; always empty for teachers. */
      completedContentIds: number[]
    }
  | { status: 'error'; message: string }

export function courseDetailError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return 'Please sign in to view this course, then try again.'
    if (error.status === 403) return 'You do not have permission to view this course.'
    if (error.status === 404) return 'This course could not be found.'
    return error.message
  }
  if (error instanceof ApiResponseError) {
    return 'The server returned an unexpected response. Please try again.'
  }
  return 'Check your connection and try again.'
}
