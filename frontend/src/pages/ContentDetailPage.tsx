import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import LoadingIndicator from '../components/ui/LoadingIndicator'
import ErrorMessage from '../components/ui/ErrorMessage'
import Button from '../components/ui/Button'
import SubmissionForm from '../components/SubmissionForm'
import SubmissionsReview from '../components/SubmissionsReview'
import { contents, enrollments, groups, modules, submissions, users } from '../services'
import { ApiError } from '../services/apiClient'
import { contentDetailError } from './contentDetailState'
import type { ContentDetailState } from './contentDetailState'

export default function ContentDetailPage() {
  const { courseId: courseIdParam, contentId: contentIdParam } =
    useParams<{ courseId: string; contentId: string }>()
  const navigate = useNavigate()
  const contentId = Number(contentIdParam)
  const isValidId = Number.isFinite(contentId) && Number.isFinite(Number(courseIdParam))
  const [state, setState] = useState<ContentDetailState>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)
  const [deleting, setDeleting] = useState(false)
  const [deleteError, setDeleteError] = useState<string | null>(null)

  useEffect(() => {
    if (!isValidId) return

    const controller = new AbortController()
    const options = { signal: controller.signal }

    async function load() {
      const content = await contents.getContent(contentId, options)
      const module = await modules.getModule(content.moduleId, options)
      const me = await users.getCurrentUser(options)
      const enrollment = await enrollments.getEnrollment(module.courseId, me.email, options)
      const isTeacher = enrollment.role === 'TEACHER'

      let ownSubmission = null
      let needsGroup = false
      let groupName: string | null = null
      const isSubmittable = content.type === 'Assignment' || content.type === 'CodingExercise'
      if (!isTeacher && isSubmittable && content.groupSubmission) {
        try {
          groupName = (await groups.getOwnGroup(module.courseId, options)).name
        } catch (error) {
          if (error instanceof ApiError && error.status === 404) {
            needsGroup = true
          } else {
            throw error
          }
        }
      }
      if (!isTeacher && isSubmittable && !needsGroup) {
        try {
          ownSubmission = await submissions.getOwnSubmission(content.id, options)
        } catch (error) {
          if (!(error instanceof ApiError && error.status === 404)) throw error
        }
      }

      return { content, isTeacher, ownSubmission, needsGroup, groupName }
    }

    load().then(
      result => {
        if (!controller.signal.aborted) setState({ status: 'success', ...result })
      },
      error => {
        if (!controller.signal.aborted) {
          setState({ status: 'error', message: contentDetailError(error) })
        }
      },
    )

    return () => controller.abort()
  }, [contentId, isValidId, attempt])

  if (!isValidId) {
    return (
      <div className="course-state course-error" role="alert">
        <h2>Unable to load content</h2>
        <p>This content could not be found.</p>
        <Link className="text-link" to="/courses">Back to courses →</Link>
      </div>
    )
  }

  function reload() {
    setState({ status: 'loading' })
    setAttempt(previous => previous + 1)
  }

  async function handleDelete() {
    if (!window.confirm('Delete this content? This cannot be undone.')) return
    setDeleting(true)
    setDeleteError(null)
    try {
      await contents.deleteContent(contentId, {})
      navigate(`/courses/${courseIdParam}`)
    } catch (error) {
      setDeleteError(contentDetailError(error))
      setDeleting(false)
    }
  }

  if (state.status === 'loading') {
    return (
      <div className="course-state">
        <LoadingIndicator message="Loading content…" />
      </div>
    )
  }

  if (state.status === 'error') {
    return (
      <ErrorMessage title="Unable to load content" message={state.message}>
        <button className="refresh-button" type="button" onClick={reload}>Try again</button>
      </ErrorMessage>
    )
  }

  const { content, isTeacher, ownSubmission, needsGroup, groupName } = state

  return (
    <article className="content-detail" aria-labelledby="content-title">
      <Link className="text-link back-link" to={`/courses/${courseIdParam}`}>← Back to course</Link>
      <div className="content-detail-header">
        <div>
          <p className="eyebrow">{content.type}</p>
          <h1 id="content-title">{content.title}</h1>
        </div>
        {isTeacher && (
          <Button variant="danger" onClick={handleDelete} loading={deleting}>Delete content</Button>
        )}
      </div>
      {deleteError && <ErrorMessage message={deleteError} />}

      {content.type === 'StaticPage' && (
        <div className="content-body">{content.body}</div>
      )}

      {(content.type === 'Assignment' || content.type === 'CodingExercise') && (
        <>
          {content.instructions && <p className="content-body">{content.instructions}</p>}
          {content.repositoryUrl && (
            <p>
              Repository: <a href={content.repositoryUrl} target="_blank" rel="noreferrer">
                {content.repositoryUrl}
              </a>
            </p>
          )}
          {content.deadline && <p className="content-deadline">Deadline: {content.deadline}</p>}
          {!isTeacher && needsGroup && (
            <p className="module-placeholder">
              This is a group assignment. Ask your teacher to add you to a group before you can submit.
            </p>
          )}
          {!isTeacher && groupName && (
            <p className="module-placeholder">
              Group assignment: you are submitting as <strong>{groupName}</strong>. Everyone in
              your group shares this submission and its grade.
            </p>
          )}
          {!isTeacher && !needsGroup && (
            <SubmissionForm
              contentId={content.id}
              existingSubmission={ownSubmission}
              onSubmitted={reload}
            />
          )}
          {isTeacher && <SubmissionsReview contentId={content.id} />}
        </>
      )}

      {content.type === 'Quiz' && (
        <p className="module-placeholder">Quiz-taking is coming soon.</p>
      )}
    </article>
  )
}
