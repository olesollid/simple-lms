import { useEffect, useState } from 'react'
import CourseList from '../components/CourseList'
import { courses } from '../services'
import { courseListError } from './courseListState'
import type { CourseListState } from './courseListState'

export default function CoursesPage() {
  const [state, setState] = useState<CourseListState>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    const controller = new AbortController()

    courses.getCourses({ signal: controller.signal }).then(
      result => {
        if (!controller.signal.aborted) setState({ status: 'success', courses: result })
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
          <button className="refresh-button" type="button" onClick={reload}
            disabled={state.status === 'loading'}>
            {state.status === 'error' ? 'Try again' : 'Refresh courses'}
          </button>
        </div>
        <section aria-label="Course results" aria-busy={state.status === 'loading'}>
          <CourseList state={state} />
        </section>
    </>
  )
}
