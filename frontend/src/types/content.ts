export type ContentType = 'StaticPage' | 'Quiz' | 'Assignment' | 'CodingExercise'

export interface ContentResponse {
  id: number
  moduleId: number
  type: ContentType
  title: string
  position: number
  visibleFrom: string | null
  visibleUntil: string | null
  body: string | null
  instructions: string | null
  repositoryUrl: string | null
  deadline: string | null
  lateSubmissionsAccepted: boolean | null
  groupSubmission: boolean | null
}

export interface StaticPageRequest {
  title: string
  position: number
  body: string
  visibleFrom: string | null
  visibleUntil: string | null
}

export interface QuizRequest {
  title: string
  position: number
  maxAttempts: number
  showHints: boolean
  deadline: string | null
}

export interface AssignmentRequest {
  title: string
  position: number
  instructions: string
  deadline: string | null
  lateSubmissionsAccepted: boolean
  groupSubmission: boolean
}

export interface CodingExerciseRequest {
  title: string
  position: number
  repositoryUrl: string
  deadline: string | null
  lateSubmissionsAccepted: boolean
  groupSubmission: boolean
}
