import { useEffect, useState } from 'react'
import { quizzes } from '../services'
import { ApiError, ApiResponseError } from '../services/apiClient'
import Button from './ui/Button'
import ErrorMessage from './ui/ErrorMessage'
import LoadingIndicator from './ui/LoadingIndicator'
import { BLANK } from '../types/quiz'
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
 * queue), so after submitting it polls until grading has finished. The grade itself stays hidden
 * until the teacher publishes the results; until then the student sees that the attempt was
 * submitted. The best attempt counts.
 */
export default function QuizTaker({ quizId }: { quizId: number }) {
  const [state, setState] = useState<TakerState>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)
  const [answering, setAnswering] = useState(false)
  const [selected, setSelected] = useState<Record<number, number[]>>({})
  // Chosen order per ORDERING question; until the student moves something, the order shown is the one served.
  const [orders, setOrders] = useState<Record<number, number[]>>({})
  // MATCHING: the right-hand item id chosen for each left-hand item (by position), per question.
  const [matchChoices, setMatchChoices] = useState<Record<number, Record<number, number>>>({})
  // FILL_IN: what was typed into each blank, per question.
  const [typed, setTyped] = useState<Record<number, string[]>>({})
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
  const gradingPending = attempts != null
    && attempts.attemptsUsed > (attempts.attemptsGraded ?? attempts.latest?.attemptNumber ?? 0)

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

  const orderedIds = (question: Question): number[] =>
    orders[question.id] ?? question.options.map(option => option.id)

  function move(question: Question, from: number, to: number) {
    setOrders(previous => {
      const current = [...(previous[question.id] ?? question.options.map(option => option.id))]
      const [moved] = current.splice(from, 1)
      current.splice(to, 0, moved)
      return { ...previous, [question.id]: current }
    })
  }

  function choose(question: Question, index: number, rightId: number | null) {
    setMatchChoices(previous => {
      const current = { ...(previous[question.id] ?? {}) }
      if (rightId == null) delete current[index]
      else current[index] = rightId
      return { ...previous, [question.id]: current }
    })
  }

  function type(question: Question, blank: number, value: string) {
    setTyped(previous => {
      const current = [...(previous[question.id] ?? [])]
      current[blank] = value
      return { ...previous, [question.id]: current }
    })
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setSubmitError(null)
    try {
      const answers: Record<number, number[]> = { ...selected }
      const textAnswers: Record<number, string[]> = {}
      if (state.status === 'ready') {
        for (const question of state.questions) {
          if (question.type === 'ORDERING') {
            answers[question.id] = orderedIds(question)
          } else if (question.type === 'MATCHING') {
            // A pair left unchosen makes the whole question wrong, so there is nothing to send.
            const chosen = question.options.map((_, index) => matchChoices[question.id]?.[index])
            if (chosen.every(rightId => rightId != null)) answers[question.id] = chosen as number[]
          } else if (question.type === 'FILL_IN') {
            textAnswers[question.id] = Array.from({ length: question.text.split(BLANK).length - 1 },
              (_, blank) => typed[question.id]?.[blank] ?? '')
          }
        }
      }
      const body = Object.keys(textAnswers).length > 0 ? { answers, textAnswers } : { answers }
      const { attemptNumber } = await quizzes.submit(quizId, body, {})
      setState(previous => previous.status === 'ready'
        ? { ...previous, attempts: { ...previous.attempts, attemptsUsed: attemptNumber } }
        : previous)
      setAnswering(false)
      setSelected({})
      setOrders({})
      setMatchChoices({})
      setTyped({})
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
  const resultsPublished = state.attempts.resultsPublished === true
  const questionsWithHints = questions.filter(question => hints[question.id])
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
          Each question is worth 1 point and counts only if it is <strong>completely</strong> right:
          every correct option selected and no wrong ones, the whole order right, every pair
          matched, or every blank filled in. A partly correct answer gives 0 points.
        </p>
        <ol className="quiz-question-list">
          {questions.map(question => (
            <li key={question.id} className="quiz-question">
              <fieldset>
                <legend className="quiz-question-text">
                  {question.type === 'FILL_IN' ? `Question ${question.position + 1}: fill in the blanks` : question.text}
                </legend>
                {question.type === 'ORDERING' ? (
                  <>
                    <p className="quiz-type-label">
                      Put these in the right order, first to last. Use the buttons to move an item.
                    </p>
                    <ol className="quiz-order-list">
                      {orderedIds(question).map((optionId, index, ids) => {
                        const option = question.options.find(candidate => candidate.id === optionId)!
                        return (
                          <li key={optionId} className="quiz-order-item">
                            <span>{option.text}</span>
                            <Button
                              variant="secondary"
                              aria-label={`Move ${option.text} up`}
                              disabled={index === 0}
                              onClick={() => move(question, index, index - 1)}
                            >
                              <span aria-hidden="true">↑</span>
                            </Button>
                            <Button
                              variant="secondary"
                              aria-label={`Move ${option.text} down`}
                              disabled={index === ids.length - 1}
                              onClick={() => move(question, index, index + 1)}
                            >
                              <span aria-hidden="true">↓</span>
                            </Button>
                          </li>
                        )
                      })}
                    </ol>
                  </>
                ) : question.type === 'MATCHING' ? (
                  <>
                    <p className="quiz-type-label">Choose the item that goes with each one.</p>
                    <ul className="quiz-match-list">
                      {question.options.map((left, index) => (
                        <li key={left.id} className="quiz-match-item">
                          <label htmlFor={`match-${question.id}-${index}`}>{left.text}</label>
                          <select
                            id={`match-${question.id}-${index}`}
                            className="ui-input"
                            value={matchChoices[question.id]?.[index] ?? ''}
                            onChange={event => choose(question, index,
                              event.target.value === '' ? null : Number(event.target.value))}
                          >
                            <option value="">Choose…</option>
                            {(question.matches ?? []).map(right => (
                              <option key={right.id} value={right.id}>{right.text}</option>
                            ))}
                          </select>
                        </li>
                      ))}
                    </ul>
                  </>
                ) : question.type === 'FILL_IN' ? (
                  <p className="quiz-fill-in">
                    {question.text.split(BLANK).map((piece, blank, pieces) => (
                      <span key={blank}>
                        {piece}
                        {blank < pieces.length - 1 && (
                          <input
                            className="ui-input quiz-blank"
                            aria-label={`Question ${question.position + 1}, blank ${blank + 1}`}
                            value={typed[question.id]?.[blank] ?? ''}
                            onChange={event => type(question, blank, event.target.value)}
                          />
                        )}
                      </span>
                    ))}
                  </p>
                ) : (
                  question.options.map(option => (
                    <label key={option.id} className="ui-checkbox-field">
                      <input
                        type="checkbox"
                        checked={selected[question.id]?.includes(option.id) ?? false}
                        onChange={event => toggle(question.id, option.id, event.target.checked)}
                      />
                      {option.text}
                    </label>
                  ))
                )}
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

      {!gradingPending && attemptsUsed > 0 && !resultsPublished && (
        <>
          <p role="status" className="quiz-pending">
            Submitted. Your result will appear here once your teacher publishes the results.
          </p>
          {questionsWithHints.length > 0 && (
            <div className="quiz-latest">
              <h3>Hints for your next attempt</h3>
              <ul className="quiz-wrong-list">
                {questionsWithHints.map(question => (
                  <li key={question.id}>
                    {question.text}
                    <p className="quiz-hint"><strong>Hint:</strong> {hints[question.id]}</p>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </>
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
