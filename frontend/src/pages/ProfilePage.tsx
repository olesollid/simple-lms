import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { courses, users } from '../services'
import { ApiError } from '../services/apiClient'
import Button from '../components/ui/Button'
import ErrorMessage from '../components/ui/ErrorMessage'
import LoadingIndicator from '../components/ui/LoadingIndicator'
import type { CourseResponse } from '../types/course'
import type { UserResponse } from '../types/user'
import type { MyActivity, MyDeliveriesResponse } from '../types/myDeliveries'

function profileError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return 'Please sign in to view your profile.'
    if (error.status === 403) return 'You no longer have access to this course.'
    if (error.status === 404) return 'This course is no longer available.'
    return error.message
  }
  return 'Unable to load your information. Check your connection and try again.'
}

export default function ProfilePage() {
  const [state, setState] = useState<{ status: 'loading' } | { status: 'error'; message: string }
    | { status: 'success'; user: UserResponse; courses: CourseResponse[] }>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    const controller = new AbortController()
    const options = { signal: controller.signal }
    Promise.all([users.getCurrentUser(options), courses.getCourses(options)]).then(([user, courseList]) => {
      if (!controller.signal.aborted) setState({ status: 'success', user,
        courses: [...courseList].sort((a, b) => b.year - a.year || a.title.localeCompare(b.title)) })
    }, error => {
      if (!controller.signal.aborted) setState({ status: 'error', message: profileError(error) })
    })
    return () => controller.abort()
  }, [attempt])

  return <>
    <div className="page-heading"><div><p className="eyebrow">My account</p><h1>Profile</h1></div>
      <Button variant="secondary" disabled={state.status === 'loading'} onClick={() => {
        setState({ status: 'loading' }); setAttempt(value => value + 1)
      }}>Refresh profile</Button>
    </div>
    {state.status === 'loading' && <LoadingIndicator message="Loading profile…" />}
    {state.status === 'error' && <ErrorMessage title="Unable to load profile" message={state.message} />}
    {state.status === 'success' && <>
      <section className="profile-personal" aria-label="Personal information">
        <h2>Personal information</h2>
        <dl><div><dt>Name</dt><dd>{state.user.name}</dd></div><div><dt>Email</dt><dd>{state.user.email}</dd></div></dl>
      </section>
      <h2 className="profile-courses-heading">My courses</h2>
      {state.courses.length === 0 ? <div className="course-state"><p>You are not enrolled in any courses yet.</p></div>
        : state.courses.map(course => <CourseProfile key={`${course.id}:${attempt}`} course={course} />)}
    </>}
  </>
}

function CourseProfile({ course }: { course: CourseResponse }) {
  const [state, setState] = useState<{ status: 'loading' } | { status: 'error'; message: string }
    | { status: 'success'; data: MyDeliveriesResponse }>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    const controller = new AbortController()
    courses.getMyDeliveries(course.id, { signal: controller.signal }).then(data => {
      if (!controller.signal.aborted) setState({ status: 'success', data })
    }, error => {
      if (!controller.signal.aborted) setState({ status: 'error', message: profileError(error) })
    })
    return () => controller.abort()
  }, [course.id, attempt])
  return <section className="profile-course" aria-labelledby={`profile-course-${course.id}`}>
    <p className="eyebrow">{course.term === 'AUTUMN' ? 'Autumn' : 'Spring'} {course.year}</p>
    <h2 id={`profile-course-${course.id}`}><Link className="text-link" to={`/courses/${course.id}`}>{course.title}</Link></h2>
    {state.status === 'loading' && <LoadingIndicator message={`Loading ${course.title} results…`} />}
    {state.status === 'error' && <ErrorMessage message={state.message}>
      <Button variant="secondary" onClick={() => { setState({ status: 'loading' }); setAttempt(value => value + 1) }}>Retry {course.title}</Button>
    </ErrorMessage>}
    {state.status === 'success' && (state.data.role === 'TEACHER'
      ? <div className="profile-teaching"><p className="role-badge role-badge--teacher">Teacher</p>
        <Link className="ui-button ui-button--secondary" to={`/courses/${course.id}/students`}>Student deliveries</Link></div>
      : state.data.activities.length === 0 ? <p>No available assignments or quizzes yet.</p> : <>
        <ActivitySection title="Published grades" courseId={course.id}
          activities={state.data.activities.filter(item => item.status === 'GRADED')} empty="No published grades yet." />
        <ActivitySection title="Submitted work" courseId={course.id}
          activities={state.data.activities.filter(item => item.status === 'AWAITING_GRADING' || item.status === 'AWAITING_PUBLICATION')}
          empty="No work awaiting results." />
        <ActivitySection title="Not submitted" courseId={course.id}
          activities={state.data.activities.filter(item => item.status === 'NOT_SUBMITTED')} empty="No outstanding work." />
      </>)}
  </section>
}

function ActivitySection({ title, activities, courseId, empty }: {
  title: string; activities: MyActivity[]; courseId: number; empty: string
}) {
  return <section className="profile-activity-section" aria-label={title}><h3>{title}</h3>
    {activities.length === 0 ? <p className="profile-empty">{empty}</p>
      : <ul className="profile-activities">{activities.map(item => <li key={item.contentId}>
        <div className="profile-activity-heading">
          <Link className="text-link" to={`/courses/${courseId}/modules/${item.moduleId}/contents/${item.contentId}`}>{item.title}</Link>
          {item.status === 'GRADED' && item.grade != null
            ? <span className="delivery-status delivery-status--graded">{Math.round(item.grade * 100) / 100} / 100 <span aria-hidden="true">✓</span></span>
            : <span className={`delivery-status delivery-status--${item.status === 'NOT_SUBMITTED' ? 'missing' : 'pending'}`}>
              {item.status === 'NOT_SUBMITTED' ? item.overdue ? 'Not submitted · Overdue' : 'Not submitted'
                : item.status === 'AWAITING_PUBLICATION' ? 'Awaiting publication' : 'Awaiting grading'}
            </span>}
        </div>
        <p className="profile-activity-meta">{item.type === 'CodingExercise' ? 'Coding exercise' : item.type}
          {item.groupSubmission && ' · Group hand-in'}{item.lateSubmission && ' · Submitted late'}</p>
        {item.deadline && <p className="profile-activity-meta">Deadline: {item.deadline.replace('T', ' ')} (Europe/Oslo)</p>}
        {item.needsGroup && <p>Ask your teacher to add you to a group before submitting.</p>}
        {item.status === 'GRADED' && item.gradingPending && <p>A newer quiz attempt is being graded.</p>}
        {item.status === 'GRADED' && item.feedback && <p className="profile-feedback">{item.feedback}</p>}
      </li>)}</ul>}
  </section>
}
