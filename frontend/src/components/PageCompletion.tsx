import { useEffect, useState } from 'react'
import { contents, modules } from '../services'
import { ApiError, ApiResponseError } from '../services/apiClient'
import Button from './ui/Button'
import ErrorMessage from './ui/ErrorMessage'

type CompletionState = 'loading' | 'not-needed' | 'not-completed' | 'completed' | 'unknown'

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message
  if (error instanceof ApiResponseError) return 'The server returned an unexpected response.'
  return 'Check your connection and try again.'
}

/**
 * Lets a student mark a page as completed. Pages are the one content type completed by hand:
 * assignments complete on submission and quizzes once an attempt is graded. Completion only
 * matters for unlocking, so nothing is shown unless the page's module is a prerequisite of
 * another module.
 */
export default function PageCompletion({ courseId, moduleId, contentId }: {
  courseId: number
  moduleId: number
  contentId: number
}) {
  const [state, setState] = useState<CompletionState>('loading')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    const options = { signal: controller.signal }
    Promise.all([contents.getOwnCompletions(courseId, options), modules.getModules(courseId, options)]).then(
      ([completions, courseModules]) => {
        if (controller.signal.aborted) return
        const unlocksSomething = courseModules.some(module => module.dependencyIds.includes(moduleId))
        if (!unlocksSomething) {
          setState('not-needed')
          return
        }
        const done = completions.some(item => item.contentId === contentId && item.completedAt != null)
        setState(done ? 'completed' : 'not-completed')
      },
      () => { if (!controller.signal.aborted) setState('unknown') },
    )
    return () => controller.abort()
  }, [courseId, moduleId, contentId])

  async function markCompleted() {
    setSaving(true)
    setError(null)
    try {
      await contents.markCompleted(contentId, {})
      setState('completed')
    } catch (markError) {
      setError(errorMessage(markError))
    } finally {
      setSaving(false)
    }
  }

  if (state === 'loading' || state === 'not-needed') return null

  return (
    <div className="page-completion">
      {state === 'completed' ? (
        <p className="completed-badge" role="status">✓ Completed</p>
      ) : (
        <>
          <Button onClick={markCompleted} loading={saving}>Mark as complete</Button>
          <p className="ui-hint">Marking this page complete counts toward unlocking the next modules.</p>
        </>
      )}
      {error && <ErrorMessage message={error} />}
    </div>
  )
}
