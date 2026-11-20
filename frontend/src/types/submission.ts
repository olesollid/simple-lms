export interface SubmissionRequest {
  text: string
}

export interface SubmissionResponse {
  id: number
  contentId: number
  /** Set for an individual submission, null for a group submission (see groupId instead). */
  submitterEmail: string | null
  /** Set for a group submission, null for an individual submission. */
  groupId: number | null
  /** Whoever most recently submitted -- the only field always set, individual or group. */
  lastSubmittedByEmail: string
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
