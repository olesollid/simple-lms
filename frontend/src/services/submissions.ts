import type { GradeRequest, SubmissionRequest, SubmissionResponse } from '../types/submission.ts'
import type { ApiCallOptions, ApiRequest } from './apiClient.ts'

export function createSubmissionService(request: ApiRequest) {
  return {
    submit: (contentId: number, body: SubmissionRequest, options?: ApiCallOptions) =>
      request<SubmissionResponse>(`/contents/${contentId}/submissions`, {
        ...options, method: 'POST', body,
      }),
    getOwnSubmission: (contentId: number, options?: ApiCallOptions) =>
      request<SubmissionResponse>(`/contents/${contentId}/submissions/mine`, options),
    getSubmissionsByContent: (contentId: number, options?: ApiCallOptions) =>
      request<SubmissionResponse[]>(`/contents/${contentId}/submissions`, options),
    grade: (submissionId: number, body: GradeRequest, options?: ApiCallOptions) =>
      request<SubmissionResponse>(`/submissions/${submissionId}/grade`, {
        ...options, method: 'PUT', body,
      }),
  }
}
