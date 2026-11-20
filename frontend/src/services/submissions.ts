import type { GradeRequest, SubmissionRequest, SubmissionResponse } from '../types/submission.ts'
import type { ApiCallOptions, ApiRequest } from './apiClient.ts'

export function createSubmissionService(request: ApiRequest) {
  return {
    upload: (contentId: number, text: string, file: File, options?: ApiCallOptions) => {
      const body = new FormData()
      body.append('text', text)
      body.append('file', file)
      return request<SubmissionResponse>(`/contents/${contentId}/submissions`, {
        ...options, method: 'POST', body,
      })
    },
    gradesPublished: (contentId: number, options?: ApiCallOptions) =>
      request<boolean>(`/contents/${contentId}/grades/publication`, options),
    publishGrades: (contentId: number, options?: ApiCallOptions) =>
      request<boolean>(`/contents/${contentId}/grades/publication`, { ...options, method: 'PUT' }),
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
