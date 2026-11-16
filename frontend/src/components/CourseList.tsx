import type { CourseListState } from '../pages/courseListState'
import ErrorMessage from './ui/ErrorMessage'
import LoadingIndicator from './ui/LoadingIndicator'
import CourseCard from './CourseCard'

export default function CourseList({ state }: { state: CourseListState }) {
  if (state.status === 'loading') {
    return (
      <div className="course-state">
        <LoadingIndicator message="Loading courses…" />
      </div>
    )
  }

  if (state.status === 'error') {
    return <ErrorMessage title="Unable to load courses" message={state.message} />
  }

  if (state.courses.length === 0) {
    return (
      <div className="course-state" role="status">
        <h2>No courses yet</h2>
        <p>Courses will appear here when they are available.</p>
      </div>
    )
  }

  return (
    <>
      <p className="course-count" role="status">
        {state.courses.length} {state.courses.length === 1 ? 'course' : 'courses'}
      </p>
      <ul className="course-grid" aria-label="Courses">
        {state.courses.map(course => (
          <li key={course.id}><CourseCard course={course} /></li>
        ))}
      </ul>
    </>
  )
}
