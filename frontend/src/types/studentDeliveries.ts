import type { ContentResponse } from './content'
import type { SubmissionResponse } from './submission'

export interface StudentDelivery {
  content: ContentResponse
  moduleTitle: string
  availableToStudent: boolean
  gradesVisibleToStudent: boolean
  submission: SubmissionResponse | null
  quiz: { attemptsUsed: number; latestGradedAttempt: number; bestScore: number | null } | null
}

export interface StudentDeliveriesResponse {
  courseId: number
  courseTitle: string
  studentEmail: string
  studentName: string
  deliveries: StudentDelivery[]
}
