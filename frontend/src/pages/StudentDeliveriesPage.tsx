import { useEffect, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import { ApiError, courses, enrollments, submissions, users } from '../services'
import { courseDetailError } from './courseDetailState'
import LoadingIndicator from '../components/ui/LoadingIndicator'
import ErrorMessage from '../components/ui/ErrorMessage'
import Button from '../components/ui/Button'
import { SubmissionReviewItem } from '../components/SubmissionsReview'
import type { EnrollmentResponse } from '../types/enrollment'
import type { StudentDeliveriesResponse, StudentDelivery } from '../types/studentDeliveries'

type RosterState = { status: 'loading' } | { status: 'error'; message: string }
  | { status: 'success'; title: string; students: EnrollmentResponse[] }

export default function StudentDeliveriesPage() {
  const { courseId } = useParams()
  const id = Number(courseId)
  if (!Number.isSafeInteger(id) || id <= 0) return <ErrorMessage title="Unable to load deliveries" message="Invalid course." />
  return <CourseDeliveries key={id} courseId={id} />
}

function CourseDeliveries({ courseId }: { courseId: number }) {
  const [state, setState] = useState<RosterState>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)
  const [search, setSearch] = useSearchParams()

  useEffect(() => {
    const controller = new AbortController()
    const options = { signal: controller.signal }
    async function load() {
      const [course, me] = await Promise.all([courses.getCourse(courseId, options), users.getCurrentUser(options)])
      const role = await enrollments.getEnrollment(courseId, me.email, options)
      if (role.role !== 'TEACHER') throw new ApiError(403, 'Only teachers can review student deliveries.', null)
      const students = await enrollments.getEnrollments(courseId, 'STUDENT', options)
      return { title: course.title, students: [...students].sort((a, b) => a.name.localeCompare(b.name) || a.email.localeCompare(b.email)) }
    }
    load().then(result => {
      if (!controller.signal.aborted) setState({ status: 'success', ...result })
    }, error => {
      if (!controller.signal.aborted) setState({ status: 'error', message: courseDetailError(error) })
    })
    return () => controller.abort()
  }, [courseId, attempt])

  const email = state.status === 'success' ? search.get('student') ?? state.students[0]?.email ?? '' : ''
  const selected = state.status === 'success' && state.students.some(student => student.email === email)

  return <>
    <Link className="text-link back-link" to={`/courses/${courseId}`}>← Back to course</Link>
    <div className="page-heading"><div>
      <p className="eyebrow">{state.status === 'success' ? state.title : 'Course administration'}</p>
      <h1>Student deliveries</h1>
    </div></div>
    {state.status === 'loading' && <LoadingIndicator message="Loading students…" />}
    {state.status === 'error' && <ErrorMessage title="Unable to load students" message={state.message}>
      <Button onClick={() => { setState({ status: 'loading' }); setAttempt(value => value + 1) }}>Try again</Button>
    </ErrorMessage>}
    {state.status === 'success' && (state.students.length === 0
      ? <div className="course-state"><h2>No students enrolled</h2><p>Add students through Manage students on the course page.</p></div>
      : <>
        <div className="deliveries-selector ui-field">
          <label className="ui-label" htmlFor="delivery-student">Student</label>
          <select className="ui-input" id="delivery-student" value={selected ? email : ''}
            onChange={event => setSearch({ student: event.target.value })}>
            {!selected && <option value="">Select a student</option>}
            {state.students.map(student => <option key={student.email} value={student.email}>{student.name} ({student.email})</option>)}
          </select>
        </div>
        {selected ? <StudentWork key={email} courseId={courseId} email={email} />
          : <ErrorMessage message="This student is no longer enrolled in the course. Select another student." />}
      </>)}
  </>
}

function StudentWork({ courseId, email }: { courseId: number; email: string }) {
  const [state, setState] = useState<{ status: 'loading' } | { status: 'error'; message: string }
    | { status: 'success'; data: StudentDeliveriesResponse }>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    const controller = new AbortController()
    submissions.getStudentDeliveries(courseId, email, { signal: controller.signal }).then(data => {
      if (!controller.signal.aborted) setState({ status: 'success', data })
    }, error => {
      if (!controller.signal.aborted) setState({ status: 'error', message: courseDetailError(error) })
    })
    return () => controller.abort()
  }, [courseId, email, attempt])
  function reload() { setState({ status: 'loading' }); setAttempt(value => value + 1) }
  if (state.status === 'loading') return <LoadingIndicator message="Loading student deliveries…" />
  if (state.status === 'error') return <ErrorMessage title="Unable to load deliveries" message={state.message}>
    <Button onClick={reload}>Try again</Button>
  </ErrorMessage>
  const { data } = state
  const delivered = data.deliveries.filter(item => item.submission || (item.quiz?.attemptsUsed ?? 0) > 0).length
  return <section aria-label={`Deliveries for ${data.studentName}`}>
    <div className="page-heading deliveries-heading"><div>
      <h2>{data.studentName}</h2><p>{data.studentEmail}</p>
      <p role="status">{delivered} of {data.deliveries.length} activities have a hand-in or quiz attempt.</p>
    </div><Button variant="secondary" onClick={reload}>Refresh deliveries</Button></div>
    {data.deliveries.length === 0 ? <div className="course-state"><h3>No assessable activities yet</h3>
      <p>Add assignments, coding exercises or quizzes to this course.</p></div>
      : <ul className="delivery-list">{data.deliveries.map(item => <DeliveryCard key={item.content.id}
        item={item} courseId={courseId} onGraded={reload} />)}</ul>}
  </section>
}

function DeliveryCard({ item, courseId, onGraded }: { item: StudentDelivery; courseId: number; onGraded: () => void }) {
  const { content, submission, quiz } = item
  const graded = quiz ? quiz.bestScore != null : submission?.grade != null
  const status = quiz
    ? quiz.attemptsUsed === 0 ? 'Not attempted' : quiz.latestGradedAttempt < quiz.attemptsUsed ? 'Grading pending' : 'Graded'
    : !submission ? 'Missing submission' : graded ? 'Graded' : 'Awaiting grading'
  const statusTone = status === 'Graded' ? 'graded'
    : status === 'Missing submission' || status === 'Not attempted' ? 'missing' : 'pending'
  return <li className="delivery-card">
    <p className="eyebrow">{item.moduleTitle} · {content.type === 'CodingExercise' ? 'Coding exercise' : content.type}</p>
    <h3><Link className="text-link" to={`/courses/${courseId}/modules/${content.moduleId}/contents/${content.id}`}>{content.title}</Link></h3>
    <p className={`delivery-status delivery-status--${statusTone}`}>
      {status}{submission?.lateSubmission === true && ' · Submitted late'}
      <svg className="delivery-status-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor"
        strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false">
        {statusTone === 'graded' ? <path d="m5 12 4 4L19 6" /> : <>
          <circle cx="12" cy="12" r="9" />
          <path d={statusTone === 'missing' ? 'M8 12h8' : 'M12 7v5l3 2'} />
        </>}
      </svg>
    </p>
    {!item.availableToStudent && <p className="hidden-badge">Not currently available to this student</p>}
    {content.groupSubmission && <p>{submission?.groupId != null ? `Shared submission from group ${submission.groupId}` : 'Group hand-in'}</p>}
    {content.deadline && <p>Deadline: {content.deadline.replace('T', ' ')} (Europe/Oslo)</p>}
    {graded && <p>{item.gradesVisibleToStudent ? 'Grade visible to student' : 'Grade not released to student'}</p>}
    {quiz && <p>{quiz.attemptsUsed} quiz {quiz.attemptsUsed === 1 ? 'attempt' : 'attempts'}
      {quiz.bestScore != null && ` · Best score: ${Math.round(quiz.bestScore * 100)}%`}</p>}
    {submission && <details className="delivery-review"><summary>Review and grade delivery</summary>
      <SubmissionReviewItem key={submission.gradedAt ?? submission.submittedAt} submission={submission} onGraded={onGraded} />
    </details>}
  </li>
}
