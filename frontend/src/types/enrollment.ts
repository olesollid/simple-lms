export type Role = 'STUDENT' | 'TEACHER'

export interface EnrollmentRequest {
  email: string
  role: Role
}

export interface EnrollmentResponse {
  id: number
  courseId: number
  courseTitle: string
  email: string
  name: string
  role: Role
  // LocalDateTime is transferred as an ISO date-time string without an offset.
  enrolledAt: string
}
