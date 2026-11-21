/**
 * CHOICE: select the correct option(s); right only if exactly the correct ones are selected.
 * ORDERING: put every option in order; right only if the whole order matches. Students always
 * receive an ordering question's options shuffled.
 */
export type QuestionType = 'CHOICE' | 'ORDERING'

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
  type: QuestionType
  options: AnswerOption[]
}

export interface QuestionWithAnswers {
  id: number
  text: string
  position: number
  hint: string | null
  type: QuestionType
  /** For ORDERING questions the options come in their correct order. */
  options: AnswerOptionWithCorrectness[]
}

export interface QuestionRequest {
  text: string
  hint: string | null
  /** Left out means CHOICE. */
  type?: QuestionType
  /** For ORDERING list the options in their correct order; `correct` is ignored. */
  options: { text: string; correct: boolean }[]
}

/** Send only what changes. The deadline is changed through the submission policy. */
export interface QuizSettingsRequest {
  /** 0 means unlimited attempts. */
  maxAttempts?: number
  /** Whether each question's hint is shown after an attempt got it wrong. */
  showHints?: boolean
}

/** Selected option ids (CHOICE) or all option ids in the chosen order (ORDERING), keyed by question id. */
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
  /** The grade that counts. Null until the attempt is graded AND the teacher publishes the results. */
  best: QuizGrade | null
  latest: QuizGrade | null
  /** Grades stay hidden until the teacher publishes the results. */
  resultsPublished: boolean
  /** How many attempts have finished grading; below attemptsUsed means one is still being graded. */
  attemptsGraded: number
  /** Hints by question id, only for questions the latest attempt got wrong, and only if the quiz shows hints. */
  hints: Record<number, string>
}

export interface QuizStudentResult {
  email: string
  name: string
  attemptsUsed: number
  /** Null until the student has a graded attempt. */
  best: QuizGrade | null
}
