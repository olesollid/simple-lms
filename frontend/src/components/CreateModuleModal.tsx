import { useState } from 'react'
import { modules } from '../services'
import { ApiError, ApiResponseError } from '../services/apiClient'
import Modal from './ui/Modal'
import Input from './ui/Input'
import Button from './ui/Button'
import ErrorMessage from './ui/ErrorMessage'
import type { ModuleResponse } from '../types/module'

function submitError(error: unknown): string {
  if (error instanceof ApiError) return error.message
  if (error instanceof ApiResponseError) return 'The server returned an unexpected response.'
  return 'Check your connection and try again.'
}

export default function CreateModuleModal({ courseId, open, onClose, onCreated }: {
  courseId: number
  open: boolean
  onClose: () => void
  onCreated: (module: ModuleResponse) => void
}) {
  const [title, setTitle] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  function reset() {
    setTitle('')
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
      const module = await modules.createModule(courseId, { title }, {})
      reset()
      onCreated(module)
    } catch (submitAttemptError) {
      setError(submitError(submitAttemptError))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal open={open} onClose={handleClose} title="Create a module">
      <form onSubmit={handleSubmit} className="create-course-form">
        <Input
          label="Title"
          value={title}
          onChange={event => setTitle(event.target.value)}
          required
        />
        {error && <ErrorMessage message={error} />}
        <Button type="submit" loading={submitting}>Create module</Button>
      </form>
    </Modal>
  )
}
