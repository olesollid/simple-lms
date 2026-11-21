/** Marks a blank in the text of a FILL_IN question. */
export const BLANK = '___'

/**
 * Every question is worth one point, with no partial credit.
 * CHOICE: select the correct option(s); right only if exactly the correct ones are selected.
 * ORDERING: put every option in order; right only if the whole order matches.
 * MATCHING: choose a right-hand item for each left-hand item; right only if every pair is right.
 * FILL_IN: type an answer for each blank (`___`) in the text; right only if every blank is right,
 * ignoring case and extra spaces.
 * Students always receive ordering options and matching right-hand items shuffled.
 */
export type QuestionType = 'CHOICE' | 'ORDERING' | 'MATCHING' | 'FILL_IN'

export interface AnswerOption {
  id: number
  text: string
}

export interface AnswerOptionWithCorrectness extends AnswerOption {
  correct: boolean
  /** MATCHING only: the right-hand item that goes with `text`. */
  matchText?: string | null
}

/** A question as students see it: no correct flags and no hint (see QuizAttempts.hints). */
export interface Question {
  id: number
  text: string
  position: number
  type: QuestionType
  /**
   * CHOICE and ORDERING: the options. MATCHING: the left-hand items in order (their ids are just
   * positions). FILL_IN: empty; the blanks are the `___` in the text.
   */
  options: AnswerOption[]
  /** MATCHING only: the shuffled right-hand items. Each answer is one of their ids. */
  matches: AnswerOption[]
}

export interface QuestionWithAnswers {
  id: number
  text: string
  position: number
  hint: string | null
  type: QuestionType
  /**
   * In the order the teacher entered them: for ORDERING the correct order, for MATCHING the pairs,
   * for FILL_IN one entry per blank whose `text` lists the accepted answers separated by `|`.
   */
  options: AnswerOptionWithCorrectness[]
}

export interface QuestionRequest {
  text: string
  hint: string | null
  /** Left out means CHOICE. */
  type?: QuestionType
  /**
   * CHOICE: options with `correct` flags. ORDERING: the options in their correct order. MATCHING:
   * `text` is the left-hand item and `matchText` the right-hand one. FILL_IN: one entry per blank
   * whose `text` lists the accepted answers separated by `|`. `correct` only matters for CHOICE.
   */
  options: { text: string; correct: boolean; matchText?: string | null }[]
}

/** Send only what changes. The deadline is changed through the submission policy. */
export interface QuizSettingsRequest {
  /** 0 means unlimited attempts. */
  maxAttempts?: number
  /** Whether each question's hint is shown after an attempt got it wrong. */
  showHints?: boolean
}

/**
 * `answers`, keyed by question id: the selected option ids (CHOICE), all option ids in the chosen
 * order (ORDERING), or one right-hand id per left-hand item in order (MATCHING).
 * `textAnswers`: the typed answer for each blank, in order (FILL_IN).
 */
export interface QuizSubmissionRequest {
  answers: Record<number, number[]>
  textAnswers?: Record<number, string[]>
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
