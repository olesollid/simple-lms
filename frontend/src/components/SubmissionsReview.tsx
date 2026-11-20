import { useEffect, useState } from 'react'
import { submissions } from '../services'
import { ApiError, ApiResponseError } from '../services/apiClient'
import LoadingIndicator from './ui/LoadingIndicator'
import ErrorMessage from './ui/ErrorMessage'
import Button from './ui/Button'
import SubmissionAttachment from './SubmissionAttachment'
import type { SubmissionResponse } from '../types/submission'

type ReviewState =
  | { status: 'loading' }
  | { status: 'success'; submissions: SubmissionResponse[]; published: boolean }
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
  const [publishing, setPublishing] = useState(false)
  const [publishError, setPublishError] = useState<string | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    Promise.all([
      submissions.getSubmissionsByContent(contentId, { signal: controller.signal }),
      submissions.gradesPublished(contentId, { signal: controller.signal }),
    ]).then(
      ([result, published]) => {
        if (!controller.signal.aborted) setState({ status: 'success', submissions: result, published })
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

  async function publish() {
    setPublishing(true)
    setPublishError(null)
    try {
      await submissions.publishGrades(contentId)
      setState(previous => previous.status === 'success' ? { ...previous, published: true } : previous)
    } catch (error) {
      setPublishError(loadError(error))
    } finally {
      setPublishing(false)
    }
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
      <p role="status">{state.published ? 'Grades are published and visible to students.' : 'Grades and feedback are hidden from students.'}</p>
      <p>Publishing applies to this content item, including grades saved later. Published grade changes remain visible.</p>
      <Button onClick={publish} loading={publishing} disabled={state.published || !state.submissions.some(item => item.gradedAt != null)}>
        {state.published ? 'Grades published' : 'Publish grades'}
      </Button>
      {publishError && <ErrorMessage message={publishError} />}
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
  const [editing, setEditing] = useState(false)
  const [grade, setGrade] = useState(submission.grade?.toString() ?? '')
  const [feedback, setFeedback] = useState(submission.feedback ?? '')
  const [grading, setGrading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  function cancelEditing() {
    setGrade(submission.grade?.toString() ?? '')
    setFeedback(submission.feedback ?? '')
    setError(null)
    setEditing(false)
  }

  async function handleGrade(event: React.FormEvent) {
    event.preventDefault()
    const gradeValue = Number(grade)
    if (!Number.isFinite(gradeValue) || gradeValue < 0 || gradeValue > 100) {
      setError('Enter a valid number between 0 and 100')
      return
    }
    setGrading(true)
    setError(null)
    try {
      await submissions.grade(submission.id, { grade: gradeValue, feedback: feedback || null }, {})
      setEditing(false)
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
      <SubmissionAttachment submission={submission} />

      {isGraded && !editing ? (
        <div className="submission-grade">
          <p><strong>Grade:</strong> {submission.grade}</p>
          {submission.feedback && <p><strong>Feedback:</strong> {submission.feedback}</p>}
          <Button variant="secondary" onClick={() => setEditing(true)}>Change grade</Button>
        </div>
      ) : (
        <form onSubmit={handleGrade} className="grade-form">
          <div className="ui-field">
            <label className="ui-label" htmlFor={`grade-${submission.id}`}>
              Grade<span className="ui-required" aria-hidden="true"> *</span>
            </label>
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
          <div className="page-heading-actions">
            <Button type="submit" loading={grading}>{isGraded ? 'Update grade' : 'Submit grade'}</Button>
            {isGraded && (
              <Button variant="secondary" onClick={cancelEditing} disabled={grading}>Cancel</Button>
            )}
          </div>
        </form>
      )}
    </div>
  )
}
