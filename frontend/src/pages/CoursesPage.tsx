import { useEffect, useState } from 'react'
import Button from '../components/ui/Button'
import SuccessMessage from '../components/ui/SuccessMessage'
import CourseList from '../components/CourseList'
import CreateCourseModal from '../components/CreateCourseModal'
import { courses, enrollments, users } from '../services'
import { courseListError } from './courseListState'
import type { CourseListState } from './courseListState'

export default function CoursesPage() {
  const [state, setState] = useState<CourseListState>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)
  const [createOpen, setCreateOpen] = useState(false)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    const options = { signal: controller.signal }

    async function load() {
      const [courseList, me] = await Promise.all([
        courses.getCourses(options),
        users.getCurrentUser(options),
      ])
      const roles = await Promise.all(
        courseList.map(course => enrollments.getEnrollment(course.id, me.email, options)),
      )
      const roleByCourseId = Object.fromEntries(
        courseList.map((course, index) => [course.id, roles[index].role]),
      )
      return { courses: courseList, roleByCourseId }
    }

    load().then(
      result => {
        if (!controller.signal.aborted) setState({ status: 'success', ...result })
      },
      error => {
        if (!controller.signal.aborted) setState({ status: 'error', message: courseListError(error) })
      },
    )

    return () => controller.abort()
  }, [attempt])

  function reload() {
    setState({ status: 'loading' })
    setAttempt(previous => previous + 1)
  }

  return (
    <>
        <div className="page-heading">
          <div>
            <p className="eyebrow">Course catalogue</p>
            <h1>Courses</h1>
            <p className="page-description">Explore course topics and find your next place to learn.</p>
          </div>
          <div className="page-heading-actions">
            <Button onClick={() => setCreateOpen(true)}>Create course</Button>
            <Button variant="secondary" className="refresh-button" onClick={reload} loading={state.status === 'loading'}>
              {state.status === 'error' ? 'Try again' : 'Refresh courses'}
            </Button>
          </div>
        </div>
        {successMessage && <SuccessMessage message={successMessage} />}
        <section aria-label="Course results" aria-busy={state.status === 'loading'}>
          <CourseList state={state} />
        </section>
        <CreateCourseModal
          open={createOpen}
          onClose={() => setCreateOpen(false)}
          onCreated={() => {
            setCreateOpen(false)
            setSuccessMessage('Course created.')
            reload()
          }}
        />
    </>
  )
}
