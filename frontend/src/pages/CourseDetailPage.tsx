import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import ModuleList from '../components/ModuleList'
import EditCourseModal from '../components/EditCourseModal'
import ManageGroupsModal from '../components/ManageGroupsModal'
import MyGroup from '../components/MyGroup'
import CreateModuleModal from '../components/CreateModuleModal'
import ManageEnrollmentsModal from '../components/ManageEnrollmentsModal'
import LoadingIndicator from '../components/ui/LoadingIndicator'
import ErrorMessage from '../components/ui/ErrorMessage'
import SuccessMessage from '../components/ui/SuccessMessage'
import Button from '../components/ui/Button'
import { contents, courses, enrollments, modules, users } from '../services'
import { courseDetailError } from './courseDetailState'
import type { CourseDetailState } from './courseDetailState'

export default function CourseDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const courseId = Number(id)
  const isValidId = Number.isFinite(courseId)
  const [state, setState] = useState<CourseDetailState>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)
  const [createModuleOpen, setCreateModuleOpen] = useState(false)
  const [manageStudentsOpen, setManageStudentsOpen] = useState(false)
  const [manageGroupsOpen, setManageGroupsOpen] = useState(false)
  const [editCourseOpen, setEditCourseOpen] = useState(false)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const [deletingCourse, setDeletingCourse] = useState(false)
  const [deleteCourseError, setDeleteCourseError] = useState<string | null>(null)

  useEffect(() => {
    if (!isValidId) return

    const controller = new AbortController()
    const options = { signal: controller.signal }

    async function load() {
      const [course, moduleList, me] = await Promise.all([
        courses.getCourse(courseId, options),
        modules.getModules(courseId, options),
        users.getCurrentUser(options),
      ])
      const enrollment = await enrollments.getEnrollment(courseId, me.email, options)
      const isTeacher = enrollment.role === 'TEACHER'
      const completedContentIds = isTeacher ? [] : (await contents.getOwnCompletions(courseId, options))
        .filter(item => item.completedAt != null)
        .map(item => item.contentId)
      return { course, modules: moduleList, isTeacher, completedContentIds }
    }

    load().then(
      result => {
        if (!controller.signal.aborted) setState({ status: 'success', ...result })
      },
      error => {
        if (!controller.signal.aborted) setState({ status: 'error', message: courseDetailError(error) })
      },
    )

    return () => controller.abort()
  }, [courseId, isValidId, attempt])

  if (!isValidId) {
    return (
      <div className="course-state course-error" role="alert">
        <h2>Unable to load course</h2>
        <p>This course could not be found.</p>
        <Link className="text-link" to="/courses">Back to courses →</Link>
      </div>
    )
  }

  function reload() {
    setState({ status: 'loading' })
    setAttempt(previous => previous + 1)
  }

  /**
   * After a module change (reorder, prerequisites, add, delete) only the module list is fetched
   * again and swapped in place. A full reload would replace the page with a spinner for a moment,
   * which flashes white and jumps back to the top of the page.
   */
  async function refreshModules() {
    try {
      const moduleList = await modules.getModules(courseId, {})
      setState(previous => previous.status === 'success' ? { ...previous, modules: moduleList } : previous)
    } catch {
      reload()
    }
  }

  async function handleDeleteCourse() {
    if (!window.confirm('Delete this course? This cannot be undone.')) return
    setDeletingCourse(true)
    setDeleteCourseError(null)
    try {
      await courses.deleteCourse(courseId, {})
      navigate('/courses')
    } catch (error) {
      setDeleteCourseError(courseDetailError(error))
      setDeletingCourse(false)
    }
  }

  if (state.status === 'loading') {
    return (
      <div className="course-state">
        <LoadingIndicator message="Loading course…" />
      </div>
    )
  }

  if (state.status === 'error') {
    return (
      <div className="course-state course-error" role="alert">
        <h2>Unable to load course</h2>
        <p>{state.message}</p>
        <button className="refresh-button" type="button" onClick={reload}>Try again</button>
        <Link className="text-link" to="/courses">Back to courses →</Link>
      </div>
    )
  }

  const { course, modules: courseModules, isTeacher, completedContentIds } = state
  const semester = `${course.term === 'AUTUMN' ? 'Autumn' : 'Spring'} ${course.year}`

  return (
    <>
      <Link className="text-link back-link" to="/courses">← Back to courses</Link>
      <div className="page-heading">
        <div>
          <p className="eyebrow">{semester}</p>
          <h1>
            {course.title}
            <span className={`role-badge ${isTeacher ? 'role-badge--teacher' : 'role-badge--student'}`}>
              {isTeacher ? 'Teacher' : 'Student'}
            </span>
          </h1>
          <p className="page-description">{course.description.trim() || 'No description provided.'}</p>
        </div>
        {isTeacher && (
          <div className="page-heading-actions">
            <Button variant="secondary" onClick={() => setManageStudentsOpen(true)}>
              Manage students
            </Button>
            <Button variant="secondary" onClick={() => setManageGroupsOpen(true)}>
              Manage groups
            </Button>
            <Button variant="secondary" className="refresh-button" onClick={() => setCreateModuleOpen(true)}>
              Add module
            </Button>
            <Button variant="secondary" onClick={() => setEditCourseOpen(true)}>
              Edit course
            </Button>
            <Button variant="danger" onClick={handleDeleteCourse} loading={deletingCourse}>
              Delete course
            </Button>
          </div>
        )}
      </div>
      {successMessage && <SuccessMessage message={successMessage} />}
      {deleteCourseError && <ErrorMessage message={deleteCourseError} />}
      {!isTeacher && <MyGroup courseId={courseId} />}
      {!isTeacher && courseModules.some(module => module.dependencyIds.length > 0) && (
        <div className="completion-rules" role="note">
          <strong>How modules unlock</strong>
          <span>
            Some modules unlock only when every item in the modules before them is completed. Pages:
            press <em>Mark as complete</em>. Assignments and coding exercises: complete when you (or your
            group) hand in. Quizzes: complete once an attempt is graded, whatever the score.
          </span>
        </div>
      )}
      <section aria-label="Course modules">
        <ModuleList
          modules={courseModules}
          isTeacher={isTeacher}
          completedContentIds={completedContentIds}
          onModuleDeleted={refreshModules}
          onModulesChanged={refreshModules}
        />
      </section>
      <EditCourseModal
        course={course}
        open={editCourseOpen}
        onClose={() => setEditCourseOpen(false)}
        onSaved={saved => {
          setEditCourseOpen(false)
          setState(previous => previous.status === 'success' ? { ...previous, course: saved } : previous)
          setSuccessMessage('Course updated.')
        }}
      />
      <CreateModuleModal
        courseId={courseId}
        open={createModuleOpen}
        onClose={() => setCreateModuleOpen(false)}
        onCreated={() => {
          setCreateModuleOpen(false)
          refreshModules()
        }}
      />
      <ManageGroupsModal
        courseId={courseId}
        open={manageGroupsOpen}
        onClose={() => setManageGroupsOpen(false)}
      />
      <ManageEnrollmentsModal
        courseId={courseId}
        open={manageStudentsOpen}
        onClose={() => setManageStudentsOpen(false)}
      />
    </>
  )
}
