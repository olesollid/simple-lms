import { useState } from 'react'
import { contents } from '../services'
import { ApiError, ApiResponseError } from '../services/apiClient'
import Modal from './ui/Modal'
import Input from './ui/Input'
import Button from './ui/Button'
import ErrorMessage from './ui/ErrorMessage'
import SubmissionPolicyFields from './SubmissionPolicyFields'
import type { ContentResponse, ContentType } from '../types/content'

function submitError(error: unknown): string {
  if (error instanceof ApiError) return error.message
  if (error instanceof ApiResponseError) return 'The server returned an unexpected response.'
  return 'Check your connection and try again.'
}

export default function CreateContentModal({ moduleId, open, onClose, onCreated }: {
  moduleId: number
  open: boolean
  onClose: () => void
  onCreated: (content: ContentResponse) => void
}) {
  const [type, setType] = useState<ContentType>('StaticPage')
  const [title, setTitle] = useState('')
  const [body, setBody] = useState('')
  const [instructions, setInstructions] = useState('')
  const [repositoryUrl, setRepositoryUrl] = useState('')
  const [groupSubmission, setGroupSubmission] = useState(false)
  const [deadline, setDeadline] = useState('')
  const [acceptsLate, setAcceptsLate] = useState(false)
  const [maxAttempts, setMaxAttempts] = useState(1)
  const [unlimitedAttempts, setUnlimitedAttempts] = useState(false)
  const [showResultsImmediately, setShowResultsImmediately] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  function reset() {
    setType('StaticPage')
    setTitle('')
    setBody('')
    setInstructions('')
    setRepositoryUrl('')
    setGroupSubmission(false)
    setDeadline('')
    setAcceptsLate(false)
    setMaxAttempts(1)
    setUnlimitedAttempts(false)
    setShowResultsImmediately(true)
    setError(null)
  }

  function handleClose() {
    reset()
    onClose()
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      let content: ContentResponse
      if (type === 'StaticPage') {
        content = await contents.createStaticPage(moduleId,
          { title, position: 0, body, visibleFrom: null, visibleUntil: null }, {})
      } else if (type === 'Quiz') {
        content = await contents.createQuiz(moduleId,
          { title, position: 0, maxAttempts: unlimitedAttempts ? 0 : maxAttempts, deadline: deadline || null,
            showResultsImmediately }, {})
      } else if (type === 'Assignment') {
        content = await contents.createAssignment(moduleId,
          { title, position: 0, instructions, deadline: deadline || null, lateSubmissionsAccepted: acceptsLate,
            groupSubmission }, {})
      } else {
        content = await contents.createCodingExercise(moduleId,
          { title, position: 0, repositoryUrl, deadline: deadline || null, lateSubmissionsAccepted: acceptsLate,
            groupSubmission }, {})
      }
      reset()
      onCreated(content)
    } catch (submitAttemptError) {
      setError(submitError(submitAttemptError))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal open={open} onClose={handleClose} title="Add content">
      <form onSubmit={handleSubmit} className="create-course-form">
        <div className="ui-field">
          <label className="ui-label" htmlFor="content-type">Type</label>
          <select
            id="content-type"
            className="ui-input"
            value={type}
            onChange={event => setType(event.target.value as ContentType)}
          >
            <option value="StaticPage">Page</option>
            <option value="Quiz">Quiz</option>
            <option value="Assignment">Assignment</option>
            <option value="CodingExercise">Coding exercise</option>
          </select>
        </div>

        <Input label="Title" value={title} onChange={event => setTitle(event.target.value)} required />

        {type === 'StaticPage' && (
          <div className="ui-field">
            <label className="ui-label" htmlFor="content-body">
              Body<span className="ui-required" aria-hidden="true"> *</span>
            </label>
            <textarea
              id="content-body"
              className="ui-input"
              rows={6}
              value={body}
              onChange={event => setBody(event.target.value)}
              required
            />
          </div>
        )}

        {type === 'Quiz' && (
          <>
            {!unlimitedAttempts && (
              <Input
                label="Max attempts"
                type="number"
                min="1"
                value={maxAttempts}
                onChange={event => setMaxAttempts(Number(event.target.value))}
                required
              />
            )}
            <label className="ui-checkbox-field">
              <input
                type="checkbox"
                checked={unlimitedAttempts}
                onChange={event => setUnlimitedAttempts(event.target.checked)}
              />
              Unlimited attempts
            </label>
            <label className="ui-checkbox-field">
              <input
                type="checkbox"
                checked={showResultsImmediately}
                onChange={event => setShowResultsImmediately(event.target.checked)}
              />
              Show results to students immediately
            </label>
            <SubmissionPolicyFields deadline={deadline} onDeadline={setDeadline} />
            <p className="module-placeholder">
              The quiz starts as a draft. Add questions and hints, then publish it for students.
            </p>
          </>
        )}

        {(type === 'Assignment' || type === 'CodingExercise') && (
          <>
            {type === 'Assignment' ? (
              <div className="ui-field">
                <label className="ui-label" htmlFor="content-instructions">
                  Instructions<span className="ui-required" aria-hidden="true"> *</span>
                </label>
                <textarea
                  id="content-instructions"
                  className="ui-input"
                  rows={6}
                  value={instructions}
                  onChange={event => setInstructions(event.target.value)}
                  required
                />
              </div>
            ) : (
              <Input
                label="Repository URL"
                value={repositoryUrl}
                onChange={event => setRepositoryUrl(event.target.value)}
                required
              />
            )}
            <label className="ui-checkbox-field">
              <input
                type="checkbox"
                checked={groupSubmission}
                onChange={event => setGroupSubmission(event.target.checked)}
              />
              Group submission
            </label>
            <SubmissionPolicyFields deadline={deadline} acceptsLate={acceptsLate}
              onDeadline={setDeadline} onAcceptsLate={setAcceptsLate} />
          </>
        )}

        {error && <ErrorMessage message={error} />}
        <Button type="submit" loading={submitting}>Add content</Button>
      </form>
    </Modal>
  )
}
