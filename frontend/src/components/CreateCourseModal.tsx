import { useState } from 'react'
import { courses } from '../services'
import { ApiError, ApiResponseError } from '../services/apiClient'
import Modal from './ui/Modal'
import Input from './ui/Input'
import Button from './ui/Button'
import ErrorMessage from './ui/ErrorMessage'
import type { CourseResponse, Term } from '../types/course'

function submitError(error: unknown): string {
  if (error instanceof ApiError) return error.message
  if (error instanceof ApiResponseError) return 'The server returned an unexpected response.'
  return 'Check your connection and try again.'
}

export default function CreateCourseModal({ open, onClose, onCreated }: {
  open: boolean
  onClose: () => void
  onCreated: (course: CourseResponse) => void
}) {
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [term, setTerm] = useState<Term>('AUTUMN')
  const [year, setYear] = useState(new Date().getFullYear())
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  function reset() {
    setTitle('')
    setDescription('')
    setTerm('AUTUMN')
    setYear(new Date().getFullYear())
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
      const course = await courses.createCourse({ title, description, term, year }, {})
      reset()
      onCreated(course)
    } catch (submitAttemptError) {
      setError(submitError(submitAttemptError))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal open={open} onClose={handleClose} title="Create a course">
      <form onSubmit={handleSubmit} className="create-course-form">
        <Input
          label="Title"
          value={title}
          onChange={event => setTitle(event.target.value)}
          required
        />
        <Input
          label="Description"
          value={description}
          onChange={event => setDescription(event.target.value)}
        />
        <div className="ui-field">
          <label className="ui-label" htmlFor="course-term">Term</label>
          <select
            id="course-term"
            className="ui-input"
            value={term}
            onChange={event => setTerm(event.target.value as Term)}
          >
            <option value="AUTUMN">Autumn</option>
            <option value="SPRING">Spring</option>
          </select>
        </div>
        <Input
          label="Year"
          type="number"
          value={year}
          onChange={event => setYear(Number(event.target.value))}
          required
        />
        {error && <ErrorMessage message={error} />}
        <Button type="submit" loading={submitting}>Create course</Button>
      </form>
    </Modal>
  )
}
