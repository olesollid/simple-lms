import { useState } from 'react'
import { submissions } from '../services'
import { ApiError, ApiResponseError } from '../services/apiClient'
import Button from './ui/Button'
import ErrorMessage from './ui/ErrorMessage'
import SubmissionAttachment from './SubmissionAttachment'
import type { SubmissionResponse } from '../types/submission'

function submitError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 409) return 'This submission has already been graded and can no longer be changed.'
    if (error.status === 404) return 'This content is no longer available to submit to.'
    return error.message
  }
  if (error instanceof ApiResponseError) return 'The server returned an unexpected response.'
  return 'Check your connection and try again.'
}

export default function SubmissionForm({ contentId, existingSubmission, onSubmitted }: {
  contentId: number
  existingSubmission: SubmissionResponse | null
  onSubmitted: () => void
}) {
  const [text, setText] = useState(existingSubmission?.text ?? '')
  const [file, setFile] = useState<File | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Re-seed the textarea when the submission itself changes (e.g. a teammate's newer group
  // submission arrives), but not on every unrelated re-render while the viewer is still typing.
  // Tracking the last-seen submittedAt and adjusting state during render (React's documented
  // pattern for this, see https://react.dev/learn/you-might-not-need-an-effect) avoids both an
  // extra render pass from a useEffect and clobbering an in-progress edit on an unrelated
  // re-render, since a fresh fetch returns a new object reference even when nothing changed.
  const [lastSeenSubmittedAt, setLastSeenSubmittedAt] = useState(existingSubmission?.submittedAt)
  if (existingSubmission?.submittedAt !== lastSeenSubmittedAt) {
    setLastSeenSubmittedAt(existingSubmission?.submittedAt)
    setText(existingSubmission?.text ?? '')
    setFile(null)
  }

  const isGraded = existingSubmission?.gradingComplete || existingSubmission?.gradedAt != null

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    if (!text.trim() && !file) {
      setError('Provide submission text or select a file.')
      return
    }
    if (file && (file.size === 0 || file.size > 10 * 1024 * 1024 || !/\.(pdf|doc|docx)$/i.test(file.name))) {
      setError('Select a non-empty PDF, DOC or DOCX file up to 10 MB.')
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      if (file) await submissions.upload(contentId, text, file, {})
      else await submissions.submit(contentId, { text }, {})
      onSubmitted()
    } catch (submitAttemptError) {
      setError(submitError(submitAttemptError))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="submission-form" aria-label="Submission">
      {existingSubmission && (
        <div className="submission-status">
          <p>Submitted {new Date(existingSubmission.submittedAt).toLocaleString()}</p>
          {existingSubmission.lateSubmission != null && <p>{existingSubmission.lateSubmission ? 'Submitted late' : 'Submitted on time'}</p>}
          <SubmissionAttachment submission={existingSubmission} />
          {existingSubmission.grade != null && (
            <div className="submission-grade">
              <p><strong>Grade:</strong> {existingSubmission.grade}</p>
              {existingSubmission.feedback && (
                <p><strong>Feedback:</strong> {existingSubmission.feedback}</p>
              )}
            </div>
          )}
        </div>
      )}

      {isGraded ? (
        <p className="module-placeholder">{existingSubmission?.grade == null
          ? 'Your submission has been graded. Results will appear when your teacher publishes them.'
          : 'This submission has been graded and can no longer be changed.'}</p>
      ) : (
        <form onSubmit={handleSubmit}>
          <div className="ui-field">
            <label className="ui-label" htmlFor="submission-text">
              {existingSubmission ? 'Update your submission' : 'Your submission'}
              <span className="ui-required" aria-hidden="true"> *</span>
            </label>
            <textarea
              id="submission-text"
              className="ui-input"
              rows={8}
              value={text}
              onChange={event => setText(event.target.value)}
            />
          </div>
          <div className="ui-field">
            <label className="ui-label" htmlFor="submission-file">Attach a document</label>
            <input key={existingSubmission?.submittedAt ?? 'new'} id="submission-file" type="file"
              accept=".pdf,.doc,.docx" disabled={submitting}
              aria-describedby="submission-file-help"
              onChange={event => setFile(event.target.files?.[0] ?? null)} />
            <p id="submission-file-help">PDF, DOC or DOCX, up to 10 MB. Text is optional when attaching a file.
              {existingSubmission?.fileName && ' Resubmitting replaces the previous file; submitting text only removes it.'}</p>
          </div>
          {error && <ErrorMessage message={error} />}
          <Button type="submit" loading={submitting}>
            {existingSubmission ? 'Resubmit' : 'Submit'}
          </Button>
        </form>
      )}
    </section>
  )
}
