import { useEffect, useState } from 'react'
import { quizzes } from '../services'
import { ApiError, ApiResponseError } from '../services/apiClient'
import ErrorMessage from './ui/ErrorMessage'
import LoadingIndicator from './ui/LoadingIndicator'
import type { QuizStudentResult } from '../types/quiz'

type ResultsState =
  | { status: 'loading' }
  | { status: 'success'; results: QuizStudentResult[] }
  | { status: 'error'; message: string }

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message
  if (error instanceof ApiResponseError) return 'The server returned an unexpected response.'
  return 'Check your connection and try again.'
}

/** Teacher view: every student in the course with their attempts and best quiz score. */
export default function QuizResults({ quizId, maxAttempts }: { quizId: number; maxAttempts: number | null }) {
  const [state, setState] = useState<ResultsState>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    quizzes.getResults(quizId, { signal: controller.signal }).then(
      results => { if (!controller.signal.aborted) setState({ status: 'success', results }) },
      error => {
        if (!controller.signal.aborted) setState({ status: 'error', message: errorMessage(error) })
      },
    )
    return () => controller.abort()
  }, [quizId, attempt])

  function reload() {
    setState({ status: 'loading' })
    setAttempt(previous => previous + 1)
  }

  const refresh = (
    <button className="refresh-button" type="button" onClick={reload}>Refresh</button>
  )

  if (state.status === 'loading') return <LoadingIndicator message="Loading results…" />

  if (state.status === 'error') {
    return (
      <ErrorMessage title="Unable to load results" message={state.message}>{refresh}</ErrorMessage>
    )
  }

  const { results } = state
  const graded = results.filter(result => result.best != null)
  const average = graded.length === 0
    ? null
    : Math.round(graded.reduce((sum, result) => sum + result.best!.score, 0) / graded.length * 100)

  return (
    <section className="quiz-results" aria-label="Student results">
      <div className="quiz-results-header">
        <h2>Student results</h2>
        {refresh}
      </div>
      {results.length === 0 ? (
        <p className="module-placeholder">No students are enrolled in this course yet.</p>
      ) : (
        <>
          <p className="module-placeholder">
            {graded.length} of {results.length} students have taken the quiz
            {average != null && ` · average best score ${average}%`}
          </p>
          <table className="quiz-results-table">
            <thead>
              <tr>
                <th scope="col">Student</th>
                <th scope="col">Attempts</th>
                <th scope="col">Best score</th>
              </tr>
            </thead>
            <tbody>
              {results.map(result => (
                <tr key={result.email}>
                  <td>
                    <div>{result.name}</div>
                    <div className="quiz-results-email">{result.email}</div>
                  </td>
                  <td>{result.attemptsUsed}{maxAttempts ? ` / ${maxAttempts}` : ''}</td>
                  <td>
                    {result.best
                      ? `${Math.round(result.best.score * 100)}% (${result.best.correctQuestions}/${result.best.totalQuestions})`
                      : result.attemptsUsed > 0 ? 'Grading…' : 'Not taken'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}
    </section>
  )
}
