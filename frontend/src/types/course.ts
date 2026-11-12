export type Term = 'SPRING' | 'AUTUMN'

export interface CourseRequest {
  title: string
  description: string
  term: Term
  year: number
}

export interface CourseResponse extends CourseRequest {
  id: number
}
