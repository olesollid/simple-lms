import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import ModuleList from '../components/ModuleList'
import LoadingIndicator from '../components/ui/LoadingIndicator'
import { courses, modules } from '../services'
import { courseDetailError } from './courseDetailState'
import type { CourseDetailState } from './courseDetailState'

export default function CourseDetailPage() {
  const { id } = useParams<{ id: string }>()
  const courseId = Number(id)
  const isValidId = Number.isFinite(courseId)
  const [state, setState] = useState<CourseDetailState>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    if (!isValidId) return

    const controller = new AbortController()

    Promise.all([
      courses.getCourse(courseId, { signal: controller.signal }),
      modules.getModules(courseId, { signal: controller.signal }),
    ]).then(
      ([course, moduleList]) => {
        if (!controller.signal.aborted) setState({ status: 'success', course, modules: moduleList })
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

  const { course, modules: courseModules } = state
  const semester = `${course.term === 'AUTUMN' ? 'Autumn' : 'Spring'} ${course.year}`

  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">{semester}</p>
          <h1>{course.title}</h1>
          <p className="page-description">{course.description.trim() || 'No description provided.'}</p>
        </div>
        <button className="refresh-button" type="button" disabled title="Coming soon">
          Manage course
        </button>
      </div>
      <section aria-label="Course modules">
        <ModuleList modules={courseModules} />
      </section>
    </>
  )
}
