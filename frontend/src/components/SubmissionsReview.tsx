import { useEffect, useState } from 'react'
import { submissions } from '../services'
import { ApiError, ApiResponseError } from '../services/apiClient'
import LoadingIndicator from './ui/LoadingIndicator'
import ErrorMessage from './ui/ErrorMessage'
import Button from './ui/Button'
import type { SubmissionResponse } from '../types/submission'

type ReviewState =
  | { status: 'loading' }
  | { status: 'success'; submissions: SubmissionResponse[] }
  | { status: 'error'; message: string }

function loadError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 403) return 'You do not have permission to view these submissions.'
    return error.message
  }
  if (error instanceof ApiResponseError) return 'The server returned an unexpected response.'
  return 'Check your connection and try again.'
}

function submitterLabel(submission: SubmissionResponse): string {
  if (submission.groupId != null) return `Group ${submission.groupId}`
  return submission.submitterEmail ?? submission.lastSubmittedByEmail
}

export default function SubmissionsReview({ contentId }: { contentId: number }) {
  const [state, setState] = useState<ReviewState>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    submissions.getSubmissionsByContent(contentId, { signal: controller.signal }).then(
      result => {
        if (!controller.signal.aborted) setState({ status: 'success', submissions: result })
      },
      error => {
        if (!controller.signal.aborted) setState({ status: 'error', message: loadError(error) })
      },
    )
    return () => controller.abort()
  }, [contentId, attempt])

  function reload() {
    setState({ status: 'loading' })
    setAttempt(previous => previous + 1)
  }

  if (state.status === 'loading') {
    return <LoadingIndicator message="Loading submissions…" />
  }

  if (state.status === 'error') {
    return (
      <ErrorMessage title="Unable to load submissions" message={state.message}>
        <button className="refresh-button" type="button" onClick={reload}>Try again</button>
      </ErrorMessage>
    )
  }

  if (state.submissions.length === 0) {
    return <p className="module-placeholder">No submissions yet.</p>
  }

  return (
    <section className="submissions-review" aria-label="Submissions">
      <h2>Submissions ({state.submissions.length})</h2>
      <ul className="submission-list">
        {state.submissions.map(submission => (
          <li key={submission.id} className="submission-review-card">
            <SubmissionReviewItem submission={submission} onGraded={reload} />
          </li>
        ))}
      </ul>
    </section>
  )
}

function SubmissionReviewItem({ submission, onGraded }: {
  submission: SubmissionResponse
  onGraded: () => void
}) {
  const isGraded = submission.gradedAt != null
  const [grade, setGrade] = useState(submission.grade?.toString() ?? '')
  const [feedback, setFeedback] = useState(submission.feedback ?? '')
  const [grading, setGrading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleGrade(event: React.FormEvent) {
    event.preventDefault()
    const gradeValue = Number(grade)
    if (!Number.isFinite(gradeValue) || gradeValue < 0) {
      setError('Enter a valid, non-negative grade.')
      return
    }
    setGrading(true)
    setError(null)
    try {
      await submissions.grade(submission.id, { grade: gradeValue, feedback: feedback || null }, {})
      onGraded()
    } catch (gradeAttemptError) {
      setError(loadError(gradeAttemptError))
    } finally {
      setGrading(false)
    }
  }

  return (
    <div>
      <div className="submission-review-header">
        <strong>{submitterLabel(submission)}</strong>
        <span className="submission-status">
          Submitted {new Date(submission.submittedAt).toLocaleString()}
          {submission.groupId != null && ` by ${submission.lastSubmittedByEmail}`}
        </span>
      </div>
      <p className="content-body">{submission.text}</p>

      {isGraded ? (
        <div className="submission-grade">
          <p><strong>Grade:</strong> {submission.grade}</p>
          {submission.feedback && <p><strong>Feedback:</strong> {submission.feedback}</p>}
        </div>
      ) : (
        <form onSubmit={handleGrade} className="grade-form">
          <div className="ui-field">
            <label className="ui-label" htmlFor={`grade-${submission.id}`}>Grade</label>
            <input
              id={`grade-${submission.id}`}
              className="ui-input"
              type="number"
              min="0"
              step="any"
              value={grade}
              onChange={event => setGrade(event.target.value)}
              required
            />
          </div>
          <div className="ui-field">
            <label className="ui-label" htmlFor={`feedback-${submission.id}`}>Feedback</label>
            <textarea
              id={`feedback-${submission.id}`}
              className="ui-input"
              rows={3}
              value={feedback}
              onChange={event => setFeedback(event.target.value)}
            />
          </div>
          {error && <ErrorMessage message={error} />}
          <Button type="submit" loading={grading}>Submit grade</Button>
        </form>
      )}
    </div>
  )
}
