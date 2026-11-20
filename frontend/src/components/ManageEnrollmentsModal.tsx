import { useEffect, useState } from 'react'
import { enrollments } from '../services'
import { ApiError, ApiResponseError } from '../services/apiClient'
import Modal from './ui/Modal'
import Input from './ui/Input'
import Button from './ui/Button'
import ErrorMessage from './ui/ErrorMessage'
import LoadingIndicator from './ui/LoadingIndicator'
import type { EnrollmentResponse } from '../types/enrollment'

function loadError(error: unknown): string {
  if (error instanceof ApiError) return error.message
  if (error instanceof ApiResponseError) return 'The server returned an unexpected response.'
  return 'Check your connection and try again.'
}

type RosterState =
  | { status: 'loading' }
  | { status: 'success'; roster: EnrollmentResponse[] }
  | { status: 'error'; message: string }

export default function ManageEnrollmentsModal({ courseId, open, onClose }: {
  courseId: number
  open: boolean
  onClose: () => void
}) {
  const [roster, setRoster] = useState<RosterState>({ status: 'loading' })
  const [rosterAttempt, setRosterAttempt] = useState(0)
  const [wasOpen, setWasOpen] = useState(open)
  const [lastReloadSeen, setLastReloadSeen] = useState(rosterAttempt)
  const [email, setEmail] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [removingEmail, setRemovingEmail] = useState<string | null>(null)

  if (open && (!wasOpen || lastReloadSeen !== rosterAttempt)) {
    setWasOpen(true)
    setLastReloadSeen(rosterAttempt)
    setRoster({ status: 'loading' })
  } else if (!open && wasOpen) {
    setWasOpen(false)
  }

  useEffect(() => {
    if (!open) return

    const controller = new AbortController()
    enrollments.getEnrollments(courseId, undefined, { signal: controller.signal }).then(
      result => {
        if (!controller.signal.aborted) setRoster({ status: 'success', roster: result })
      },
      error => {
        if (!controller.signal.aborted) setRoster({ status: 'error', message: loadError(error) })
      },
    )

    return () => controller.abort()
  }, [open, courseId, rosterAttempt])

  function reloadRoster() {
    setRosterAttempt(previous => previous + 1)
  }

  function handleClose() {
    setEmail('')
    setFormError(null)
    onClose()
  }

  async function handleEnroll(event: React.FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setFormError(null)
    try {
      await enrollments.enroll(courseId, { email, role: 'STUDENT' }, {})
      setEmail('')
      reloadRoster()
    } catch (error) {
      setFormError(loadError(error))
    } finally {
      setSubmitting(false)
    }
  }

  async function handleRemove(memberEmail: string) {
    setRemovingEmail(memberEmail)
    try {
      await enrollments.unenroll(courseId, memberEmail, {})
      reloadRoster()
    } catch (error) {
      setFormError(loadError(error))
    } finally {
      setRemovingEmail(null)
    }
  }

  return (
    <Modal open={open} onClose={handleClose} title="Manage students">
      <form onSubmit={handleEnroll} className="create-course-form">
        <Input
          label="Student email"
          type="email"
          value={email}
          onChange={event => setEmail(event.target.value)}
          required
        />
        {formError && <ErrorMessage message={formError} />}
        <Button type="submit" loading={submitting}>Enroll student</Button>
      </form>

      <div className="enrollment-roster">
        {roster.status === 'loading' && <LoadingIndicator message="Loading roster…" />}
        {roster.status === 'error' && <ErrorMessage message={roster.message} />}
        {roster.status === 'success' && roster.roster.length === 0 && (
          <p className="module-placeholder">No one is enrolled yet.</p>
        )}
        {roster.status === 'success' && roster.roster.length > 0 && (
          <ul className="roster-list">
            {roster.roster.map(member => (
              <li key={member.email} className="roster-item">
                <span>{member.name}</span>
                <span className="roster-email">{member.email}</span>
                <span className={`role-badge ${member.role === 'TEACHER' ? 'role-badge--teacher' : 'role-badge--student'}`}>
                  {member.role === 'TEACHER' ? 'Teacher' : 'Student'}
                </span>
                <Button
                  variant="secondary"
                  onClick={() => handleRemove(member.email)}
                  loading={removingEmail === member.email}
                >
                  Remove
                </Button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </Modal>
  )
}
