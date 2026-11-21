import type { ContentResponse } from '../types/content.ts'
import type {
  Question, QuestionRequest, QuestionWithAnswers, QuizAttempts, QuizStudentResult,
  QuizSubmissionRequest, QuizSubmissionResponse,
} from '../types/quiz.ts'
import type { ApiCallOptions, ApiRequest } from './apiClient.ts'

export function createQuizService(request: ApiRequest) {
  return {
    getQuestions: (quizId: number, options?: ApiCallOptions) =>
      request<Question[]>(`/quizzes/${quizId}/questions`, options),
    getQuestionsWithAnswers: (quizId: number, options?: ApiCallOptions) =>
      request<QuestionWithAnswers[]>(`/quizzes/${quizId}/questions/with-answers`, options),
    addQuestion: (quizId: number, body: QuestionRequest, options?: ApiCallOptions) =>
      request<QuestionWithAnswers>(`/quizzes/${quizId}/questions`, {
        ...options, method: 'POST', body,
      }),
    updateQuestion: (quizId: number, questionId: number, body: QuestionRequest,
      options?: ApiCallOptions) =>
      request<QuestionWithAnswers>(`/quizzes/${quizId}/questions/${questionId}`, {
        ...options, method: 'PUT', body,
      }),
    deleteQuestion: (quizId: number, questionId: number, options?: ApiCallOptions) =>
      request<void>(`/quizzes/${quizId}/questions/${questionId}`, { ...options, method: 'DELETE' }),
    setResultVisibility: (quizId: number, showResultsImmediately: boolean, options?: ApiCallOptions) =>
      request<ContentResponse>(`/quizzes/${quizId}/result-visibility`, {
        ...options, method: 'PUT', body: { showResultsImmediately },
      }),
    publish: (quizId: number, options?: ApiCallOptions) =>
      request<ContentResponse>(`/quizzes/${quizId}/publication`, { ...options, method: 'PUT' }),
    submit: (quizId: number, body: QuizSubmissionRequest, options?: ApiCallOptions) =>
      request<QuizSubmissionResponse>(`/quizzes/${quizId}/submissions`, {
        ...options, method: 'POST', body,
      }),
    getOwnAttempts: (quizId: number, options?: ApiCallOptions) =>
      request<QuizAttempts>(`/quizzes/${quizId}/attempts/mine`, options),
    getResults: (quizId: number, options?: ApiCallOptions) =>
      request<QuizStudentResult[]>(`/quizzes/${quizId}/results`, options),
  }
}
