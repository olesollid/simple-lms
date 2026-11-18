export interface SubmissionRequest {
  text: string
}

export interface SubmissionResponse {
  id: number
  contentId: number
  submitterEmail: string
  text: string
  submittedAt: string
  grade: number | null
  feedback: string | null
  gradedAt: string | null
}

export interface GradeRequest {
  grade: number
  feedback: string | null
}
