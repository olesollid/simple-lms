import { useEffect, useState } from 'react'
import { quizzes } from '../services'
import { ApiError, ApiResponseError } from '../services/apiClient'
import Button from './ui/Button'
import ErrorMessage from './ui/ErrorMessage'
import LoadingIndicator from './ui/LoadingIndicator'
import type { Question, QuizAttempts, QuizGrade } from '../types/quiz'

type TakerState =
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'ready'; questions: Question[]; attempts: QuizAttempts }

/** How often, and how many times, to ask whether the newest attempt has been graded. */
const GRADING_POLL_MS = 1000
const GRADING_POLL_LIMIT = 20

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    // 403 covers both "not enrolled" and "the deadline has passed": the server says which.
    if (error.status === 404) return 'This quiz is not available.'
    return error.message || 'You do not have access to this quiz.'
  }
  if (error instanceof ApiResponseError) return 'The server returned an unexpected response.'
  return 'Check your connection and try again.'
}

const percent = (grade: QuizGrade) => `${Math.round(grade.score * 100)}%`

/**
 * Student view of a quiz. Attempts are graded automatically (asynchronously, via the grading
 * queue), so after submitting it polls until the attempt's grade arrives. The best attempt counts.
 */
export default function QuizTaker({ quizId }: { quizId: number }) {
  const [state, setState] = useState<TakerState>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)
  const [answering, setAnswering] = useState(false)
  const [selected, setSelected] = useState<Record<number, number[]>>({})
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [polls, setPolls] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    const options = { signal: controller.signal }
    Promise.all([quizzes.getQuestions(quizId, options), quizzes.getOwnAttempts(quizId, options)]).then(
      ([questions, attempts]) => {
        if (!controller.signal.aborted) setState({ status: 'ready', questions, attempts })
      },
      error => {
        if (!controller.signal.aborted) setState({ status: 'error', message: errorMessage(error) })
      },
    )
    return () => controller.abort()
  }, [quizId, attempt])

  const attempts = state.status === 'ready' ? state.attempts : null
  // Only while results are visible can we tell when grading is done; otherwise there is nothing to wait for.
  const gradingPending = attempts != null && attempts.resultsVisible !== false
    && attempts.attemptsUsed > (attempts.latest?.attemptNumber ?? 0)

  useEffect(() => {
    if (!gradingPending || polls >= GRADING_POLL_LIMIT) return
    const timer = setTimeout(() => {
      quizzes.getOwnAttempts(quizId, {}).then(
        updated => {
          setState(previous => previous.status === 'ready' ? { ...previous, attempts: updated } : previous)
          setPolls(previous => previous + 1)
        },
        () => setPolls(previous => previous + 1),
      )
    }, GRADING_POLL_MS)
    return () => clearTimeout(timer)
  }, [gradingPending, polls, quizId])

  function reload() {
    setState({ status: 'loading' })
    setPolls(0)
    setAttempt(previous => previous + 1)
  }

  function toggle(questionId: number, optionId: number, checked: boolean) {
    setSelected(previous => {
      const current = previous[questionId] ?? []
      const next = checked ? [...current, optionId] : current.filter(id => id !== optionId)
      return { ...previous, [questionId]: next }
    })
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setSubmitError(null)
    try {
      const { attemptNumber } = await quizzes.submit(quizId, { answers: selected }, {})
      setState(previous => previous.status === 'ready'
        ? { ...previous, attempts: { ...previous.attempts, attemptsUsed: attemptNumber } }
        : previous)
      setAnswering(false)
      setSelected({})
      setPolls(0)
    } catch (error) {
      setSubmitError(errorMessage(error))
      // The quiz may have closed or run out of attempts while the student was answering. Reload
      // its state so the page shows that, instead of a form that can no longer be submitted.
      if (error instanceof ApiError && (error.status === 403 || error.status === 409)) {
        try {
          const updated = await quizzes.getOwnAttempts(quizId, {})
          setState(previous => previous.status === 'ready' ? { ...previous, attempts: updated } : previous)
          if (updated.closed) setAnswering(false)
        } catch {
          // Keep showing the submit error; the student can reload the page.
        }
      }
    } finally {
      setSubmitting(false)
    }
  }

  if (state.status === 'loading') return <LoadingIndicator message="Loading quiz…" />

  if (state.status === 'error') {
    return (
      <ErrorMessage title="Unable to load the quiz" message={state.message}>
        <button className="refresh-button" type="button" onClick={reload}>Try again</button>
      </ErrorMessage>
    )
  }

  const { questions, attempts: { attemptsUsed, maxAttempts, closed, best, latest } } = state
  const hints = state.attempts.hints ?? {}
  const unlimited = maxAttempts === 0
  const attemptsLeft = unlimited ? Infinity : Math.max(0, maxAttempts - attemptsUsed)
  const wrongLastTime = new Set(latest?.incorrectQuestionIds ?? [])
  const attemptsText = unlimited
    ? `Unlimited attempts · ${attemptsUsed} used`
    : `${attemptsUsed} of ${maxAttempts} ${maxAttempts === 1 ? 'attempt' : 'attempts'} used`

  if (questions.length === 0) {
    return <p className="module-placeholder">This quiz has no questions yet.</p>
  }

  if (!closed && (answering || (attemptsUsed === 0 && !gradingPending))) {
    return (
      <form className="quiz-taker" onSubmit={handleSubmit} aria-label="Take quiz">
        <p className="module-placeholder">
          Attempt {attemptsUsed + 1}{unlimited ? '' : ` of ${maxAttempts}`}.
        </p>
        <p className="quiz-scoring-note">
          Each question is worth 1 point. Select <strong>every</strong> correct answer and no wrong
          ones to get the point. A partly correct answer gives 0 points.
        </p>
        <ol className="quiz-question-list">
          {questions.map(question => (
            <li key={question.id} className="quiz-question">
              <fieldset>
                <legend className="quiz-question-text">{question.text}</legend>
                {question.options.map(option => (
                  <label key={option.id} className="ui-checkbox-field">
                    <input
                      type="checkbox"
                      checked={selected[question.id]?.includes(option.id) ?? false}
                      onChange={event => toggle(question.id, option.id, event.target.checked)}
                    />
                    {option.text}
                  </label>
                ))}
                {hints[question.id] && (
                  <p className="quiz-hint"><strong>Hint:</strong> {hints[question.id]}</p>
                )}
              </fieldset>
            </li>
          ))}
        </ol>
        {submitError && <ErrorMessage message={submitError} />}
        <div className="page-heading-actions">
          <Button type="submit" loading={submitting}>Submit quiz</Button>
          {attemptsUsed > 0 && (
            <Button variant="secondary" onClick={() => setAnswering(false)} disabled={submitting}>Cancel</Button>
          )}
        </div>
      </form>
    )
  }

  const wrongQuestions = questions.filter(question => wrongLastTime.has(question.id))

  return (
    <section className="quiz-result" aria-label="Quiz result">
      {best && (
        <div>
          <h2>Your grade</h2>
          <p className="quiz-score">{percent(best)}</p>
          <p>Best attempt: {best.correctQuestions} of {best.totalQuestions} questions correct.</p>
        </div>
      )}
      <p className="module-placeholder">{attemptsText}</p>
      {state.attempts.resultsVisible === false && attemptsUsed > 0 && (
        <p className="quiz-results-pending" role="status">
          Your answers are submitted. Your teacher will publish the results later.
        </p>
      )}

      {gradingPending ? (
        polls >= GRADING_POLL_LIMIT ? (
          <p role="status">
            Grading is taking longer than usual.{' '}
            <button className="refresh-button" type="button" onClick={reload}>Refresh</button>
          </p>
        ) : (
          <LoadingIndicator message={`Grading attempt ${attemptsUsed}…`} />
        )
      ) : latest && (
        <div className="quiz-latest">
          <h3>Attempt {latest.attemptNumber}: {percent(latest)}</h3>
          {wrongQuestions.length === 0 ? (
            <p>Every question correct. Well done!</p>
          ) : (
            <ul className="quiz-wrong-list">
              {wrongQuestions.map(question => (
                <li key={question.id}>
                  <span className="quiz-wrong-label">Incorrect:</span> {question.text}
                  {hints[question.id] && <p className="quiz-hint"><strong>Hint:</strong> {hints[question.id]}</p>}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}

      {!gradingPending && (closed ? (
        <p className="module-placeholder">
          {attemptsUsed === 0
            ? 'The deadline has passed. You did not take this quiz.'
            : 'The deadline has passed, so no more attempts are possible.'}
        </p>
      ) : attemptsLeft > 0 ? (
        <Button onClick={() => setAnswering(true)}>{attemptsUsed === 0 ? 'Start quiz' : 'Try again'}</Button>
      ) : (
        <p className="module-placeholder">You have used all your attempts.</p>
      ))}
    </section>
  )
}
