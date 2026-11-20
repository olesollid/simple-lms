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

export default function EditCourseModal({ course, open, onClose, onSaved }: {
  course: CourseResponse
  open: boolean
  onClose: () => void
  onSaved: (course: CourseResponse) => void
}) {
  const [title, setTitle] = useState(course.title)
  const [description, setDescription] = useState(course.description)
  const [term, setTerm] = useState<Term>(course.term)
  const [year, setYear] = useState(course.year)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  function handleClose() {
    setTitle(course.title)
    setDescription(course.description)
    setTerm(course.term)
    setYear(course.year)
    setError(null)
    onClose()
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      const saved = await courses.updateCourse(course.id, { title, description, term, year }, {})
      onSaved(saved)
    } catch (submitAttemptError) {
      setError(submitError(submitAttemptError))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal open={open} onClose={handleClose} title="Edit course">
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
          <label className="ui-label" htmlFor="edit-course-term">Term</label>
          <select
            id="edit-course-term"
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
        <Button type="submit" loading={submitting}>Save changes</Button>
      </form>
    </Modal>
  )
}
