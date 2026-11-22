import type { Role } from './enrollment'

export interface MyActivity {
  contentId: number
  moduleId: number
  title: string
  type: 'Assignment' | 'CodingExercise' | 'Quiz'
  deadline: string | null
  status: 'NOT_SUBMITTED' | 'AWAITING_GRADING' | 'AWAITING_PUBLICATION' | 'GRADED'
  grade: number | null
  feedback: string | null
  lateSubmission: boolean | null
  groupSubmission: boolean
  needsGroup: boolean
  gradingPending: boolean
  overdue: boolean
}

export interface MyDeliveriesResponse {
  courseId: number
  role: Role
  activities: MyActivity[]
}
