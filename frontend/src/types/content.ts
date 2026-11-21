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
  /** Quizzes only: 0 means unlimited attempts. */
  maxAttempts?: number | null
  /** Quizzes only: false while the quiz is a draft that students cannot see. */
  published?: boolean | null
  /** Quizzes only: true once the Europe/Oslo deadline has passed (decided by the server). */
  closed?: boolean | null
  /** Quizzes only: whether a question's hint is shown after an attempt got it wrong. */
  showHints?: boolean | null
  /** Quizzes only: true once the teacher has published the results, so students can see grades. */
  gradesPublished?: boolean | null
  /** False while students can't see it: outside its visibility window, or a draft quiz. */
  visibleToStudents?: boolean
}

/** Title and visibility window (Europe/Oslo wall-clock times, or null for no limit). */
export interface ContentMetadataRequest {
  title: string
  position: number
  visibleFrom: string | null
  visibleUntil: string | null
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
  /** 0 means unlimited attempts. */
  maxAttempts: number
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
