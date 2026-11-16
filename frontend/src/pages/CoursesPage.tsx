import { useEffect, useState } from 'react'
import Button from '../components/ui/Button'
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
      <a className="skip-link" href="#main-content">Skip to courses</a>
      <header className="app-header">
        <div className="app-header-inner">
          <span className="brand"><span className="brand-mark" aria-hidden="true">C</span>Canvas 2.0</span>
          <span className="header-label">Learning management</span>
        </div>
      </header>
      <main id="main-content" className="courses-page">
        <div className="page-heading">
          <div>
            <p className="eyebrow">Course catalogue</p>
            <h1>Courses</h1>
            <p className="page-description">Explore course topics and find your next place to learn.</p>
          </div>
          <Button variant="secondary" className="refresh-button" onClick={reload} loading={state.status === 'loading'}>
            {state.status === 'error' ? 'Try again' : 'Refresh courses'}
          </Button>
        </div>
        <section aria-label="Course results" aria-busy={state.status === 'loading'}>
          <CourseList state={state} />
        </section>
      </main>
    </>
  )
}
