import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { contents } from '../services'
import { ApiError, ApiResponseError } from '../services/apiClient'
import LoadingIndicator from './ui/LoadingIndicator'
import ErrorMessage from './ui/ErrorMessage'
import Button from './ui/Button'
import type { ContentResponse } from '../types/content'

type ContentListState =
  | { status: 'loading' }
  | { status: 'success'; contents: ContentResponse[] }
  | { status: 'error'; message: string }

function contentListError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return 'Please sign in to view this content.'
    if (error.status === 403) return 'You do not have permission to view this content.'
    return error.message
  }
  if (error instanceof ApiResponseError) return 'The server returned an unexpected response.'
  return 'Check your connection and try again.'
}

const CONTENT_TYPE_LABEL: Record<ContentResponse['type'], string> = {
  StaticPage: 'Page',
  Quiz: 'Quiz',
  Assignment: 'Assignment',
  CodingExercise: 'Coding exercise',
}

export default function ModuleContents({ courseId, moduleId, isTeacher, completedContentIds, showProgress = false }: {
  courseId: number
  moduleId: number
  isTeacher: boolean
  /** Student only: content the student has completed. Omit for teachers. */
  completedContentIds?: number[]
  /**
   * Show "x of y completed". Only meaningful when this module unlocks another one: elsewhere a
   * page can't be marked complete, so the count would never reach the end.
   */
  showProgress?: boolean
}) {
  const [state, setState] = useState<ContentListState>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)
  const [deletingId, setDeletingId] = useState<number | null>(null)
  const [deleteError, setDeleteError] = useState<string | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    contents.getContentsByModule(moduleId, { signal: controller.signal }).then(
      result => {
        if (!controller.signal.aborted) setState({ status: 'success', contents: result })
      },
      error => {
        if (!controller.signal.aborted) {
          setState({ status: 'error', message: contentListError(error) })
        }
      },
    )
    return () => controller.abort()
  }, [moduleId, attempt])

  async function move(index: number, offset: -1 | 1) {
    if (state.status !== 'success') return
    const ids = state.contents.map(item => item.id)
    const target = index + offset
    ;[ids[index], ids[target]] = [ids[target], ids[index]]
    setDeleteError(null)
    try {
      const reordered = await contents.reorder(moduleId, ids, {})
      setState({ status: 'success', contents: reordered })
    } catch (error) {
      setDeleteError(contentListError(error))
    }
  }

  async function handleDelete(item: ContentResponse) {
    if (!window.confirm(`Delete "${item.title}"? This cannot be undone.`)) return
    setDeletingId(item.id)
    setDeleteError(null)
    try {
      await contents.deleteContent(item.id, {})
      setAttempt(previous => previous + 1)
    } catch (error) {
      setDeleteError(contentListError(error))
    } finally {
      setDeletingId(null)
    }
  }

  if (state.status === 'loading') {
    return <LoadingIndicator message="Loading content…" className="module-contents-loading" />
  }

  if (state.status === 'error') {
    return <ErrorMessage message={state.message} className="module-contents-error" />
  }

  if (state.contents.length === 0) {
    return <p className="module-placeholder">No content yet.</p>
  }

  const completed = new Set(completedContentIds ?? [])
  const completedCount = state.contents.filter(item => completed.has(item.id)).length

  return (
    <>
      {deleteError && <ErrorMessage message={deleteError} className="module-contents-error" />}
      {completedContentIds && showProgress && (
        <p className="module-progress">{completedCount} of {state.contents.length} completed</p>
      )}
      <ul className="content-list" aria-label="Module content">
        {state.contents.map((item, index) => (
          <li key={item.id} className="content-item">
            <Link to={`/courses/${courseId}/modules/${moduleId}/contents/${item.id}`}>
              <span className="content-type">{CONTENT_TYPE_LABEL[item.type]}</span>
              {item.title}
            </Link>
            {completedContentIds && completed.has(item.id) && (
              <span className="completed-badge" aria-label="Completed">✓ Completed</span>
            )}
            {isTeacher && item.visibleToStudents === false && (
              <span className="hidden-badge">{item.type === 'Quiz' && item.published === false
                ? 'Draft' : 'Hidden from students'}</span>
            )}
            {isTeacher && state.contents.length > 1 && (
              <span className="reorder-buttons">
                <button type="button" className="reorder-button" aria-label={`Move ${item.title} up`}
                  disabled={index === 0} onClick={() => move(index, -1)}>↑</button>
                <button type="button" className="reorder-button" aria-label={`Move ${item.title} down`}
                  disabled={index === state.contents.length - 1} onClick={() => move(index, 1)}>↓</button>
              </span>
            )}
            {isTeacher && (
              <Button
                variant="danger"
                onClick={() => handleDelete(item)}
                loading={deletingId === item.id}
              >
                Delete
              </Button>
            )}
          </li>
        ))}
      </ul>
    </>
  )
}
