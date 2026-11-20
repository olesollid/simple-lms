import type { ContentResponse } from '../types/content.ts'
import type { SubmissionResponse } from '../types/submission.ts'
import { ApiError, ApiResponseError } from '../services/apiClient.ts'

export type ContentDetailState =
  | { status: 'loading' }
  | {
      status: 'success'
      content: ContentResponse
      isTeacher: boolean
      ownSubmission: SubmissionResponse | null
      /** True only when the content is group-submittable and the student has no group yet. */
      needsGroup: boolean
    }
  | { status: 'error'; message: string }

export function contentDetailError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return 'Please sign in to view this content, then try again.'
    if (error.status === 403) return 'You do not have permission to view this content.'
    if (error.status === 404) return 'This content could not be found.'
    return error.message
  }
  if (error instanceof ApiResponseError) {
    return 'The server returned an unexpected response. Please try again.'
  }
  return 'Check your connection and try again.'
}
