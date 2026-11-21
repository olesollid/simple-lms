export interface AnswerOption {
  id: number
  text: string
}

export interface AnswerOptionWithCorrectness extends AnswerOption {
  correct: boolean
}

/** A question as students see it: no correct flags and no hint (see QuizAttempts.hints). */
export interface Question {
  id: number
  text: string
  position: number
  options: AnswerOption[]
}

export interface QuestionWithAnswers {
  id: number
  text: string
  position: number
  hint: string | null
  options: AnswerOptionWithCorrectness[]
}

export interface QuestionRequest {
  text: string
  hint: string | null
  options: { text: string; correct: boolean }[]
}

/** Selected option ids, keyed by question id. */
export interface QuizSubmissionRequest {
  answers: Record<number, number[]>
}

export interface QuizSubmissionResponse {
  attemptNumber: number
}

export interface QuizGrade {
  quizId: number
  studentEmail: string
  attemptNumber: number
  correctQuestions: number
  totalQuestions: number
  /** Fraction between 0 and 1. */
  score: number
  incorrectQuestionIds: number[]
  gradedAt: string
}

export interface QuizAttempts {
  attemptsUsed: number
  /** 0 means unlimited. */
  maxAttempts: number
  /** Europe/Oslo wall-clock time, or null for no deadline. */
  deadline: string | null
  /** True once the deadline has passed (decided by the server, so time zones don't matter). */
  closed: boolean
  /** False while the teacher keeps results hidden; best, latest and hints are then empty. */
  resultsVisible: boolean
  /** The grade that counts. */
  best: QuizGrade | null
  latest: QuizGrade | null
  /** Hints by question id, only for questions the latest attempt got wrong. */
  hints: Record<number, string>
}

export interface QuizStudentResult {
  email: string
  name: string
  attemptsUsed: number
  /** Null until the student has a graded attempt. */
  best: QuizGrade | null
}
