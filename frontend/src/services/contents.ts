import type { AssignmentRequest, ContentMetadataRequest, CodingExerciseRequest, ContentResponse, QuizRequest,
  StaticPageRequest } from '../types/content.ts'
import type { StudentInteractionResponse } from '../types/interaction.ts'
import type { ApiCallOptions, ApiRequest } from './apiClient.ts'

export function createContentService(request: ApiRequest) {
  return {
    updateSubmissionPolicy: (id: number, body: { deadline: string | null; lateSubmissionsAccepted: boolean }, options?: ApiCallOptions) =>
      request<ContentResponse>(`/contents/${id}/submission-policy`, { ...options, method: 'PUT', body }),
    getContentsByModule: (moduleId: number, options?: ApiCallOptions) =>
      request<ContentResponse[]>(`/modules/${moduleId}/contents`, options),
    getContent: (id: number, options?: ApiCallOptions) =>
      request<ContentResponse>(`/contents/${id}`, options),
    createStaticPage: (moduleId: number, body: StaticPageRequest, options?: ApiCallOptions) =>
      request<ContentResponse>(`/modules/${moduleId}/pages`, { ...options, method: 'POST', body }),
    createQuiz: (moduleId: number, body: QuizRequest, options?: ApiCallOptions) =>
      request<ContentResponse>(`/modules/${moduleId}/quizzes`, { ...options, method: 'POST', body }),
    createAssignment: (moduleId: number, body: AssignmentRequest, options?: ApiCallOptions) =>
      request<ContentResponse>(`/modules/${moduleId}/assignments`, {
        ...options, method: 'POST', body,
      }),
    createCodingExercise: (moduleId: number, body: CodingExerciseRequest, options?: ApiCallOptions) =>
      request<ContentResponse>(`/modules/${moduleId}/coding-exercises`, {
        ...options, method: 'POST', body,
      }),
    markCompleted: (contentId: number, options?: ApiCallOptions) =>
      request<void>(`/contents/${contentId}/interactions`, { ...options, method: 'POST' }),
    getOwnCompletions: (courseId: number, options?: ApiCallOptions) =>
      request<StudentInteractionResponse[]>(`/courses/${courseId}/interactions/mine`, options),
    updateContent: (id: number, body: ContentMetadataRequest, options?: ApiCallOptions) =>
      request<ContentResponse>(`/contents/${id}`, { ...options, method: 'PUT', body }),
    updateText: (id: number, text: string, options?: ApiCallOptions) =>
      request<ContentResponse>(`/contents/${id}/text`, { ...options, method: 'PUT', body: { text } }),
    reorder: (moduleId: number, ids: number[], options?: ApiCallOptions) =>
      request<ContentResponse[]>(`/modules/${moduleId}/contents/order`, {
        ...options, method: 'PUT', body: { ids },
      }),
    deleteContent: (contentId: number, options?: ApiCallOptions) =>
      request<void>(`/contents/${contentId}`, { ...options, method: 'DELETE' }),
  }
}
