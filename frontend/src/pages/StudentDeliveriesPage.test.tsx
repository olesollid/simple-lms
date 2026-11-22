import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import StudentDeliveriesPage from './StudentDeliveriesPage'
import { ApiError } from '../services/apiClient'
import type { StudentDeliveriesResponse } from '../types/studentDeliveries'

const mocks = vi.hoisted(() => ({ getCourse: vi.fn(), getCurrentUser: vi.fn(), getEnrollment: vi.fn(),
  getEnrollments: vi.fn(), getStudentDeliveries: vi.fn(), grade: vi.fn() }))
vi.mock('../services', async () => ({
  ApiError: (await import('../services/apiClient')).ApiError,
  courses: { getCourse: mocks.getCourse }, users: { getCurrentUser: mocks.getCurrentUser },
  enrollments: { getEnrollment: mocks.getEnrollment, getEnrollments: mocks.getEnrollments },
  submissions: { getStudentDeliveries: mocks.getStudentDeliveries, grade: mocks.grade },
}))

const content = { id: 1, moduleId: 2, type: 'Assignment' as const, title: 'Essay', position: 0,
  visibleFrom: null, visibleUntil: null, body: null, instructions: 'Write', repositoryUrl: null,
  deadline: '2026-11-30T12:00:00', lateSubmissionsAccepted: true, groupSubmission: false }
const submission = { id: 10, contentId: 1, submitterEmail: 'alice@example.com', groupId: null,
  lastSubmittedByEmail: 'alice@example.com', text: 'Alice essay', submittedAt: '2026-11-30T13:00:00',
  grade: null, feedback: null, gradedAt: null, lateSubmission: true, fileName: 'essay.pdf', fileSize: 1024 }
const snapshot: StudentDeliveriesResponse = { courseId: 1, courseTitle: 'DAT250', studentName: 'Alice',
  studentEmail: 'alice@example.com', deliveries: [
    { content, moduleTitle: 'Week 1', availableToStudent: true, gradesVisibleToStudent: false, submission, quiz: null },
    { content: { ...content, id: 2, title: 'Code', type: 'CodingExercise' }, moduleTitle: 'Week 1',
      availableToStudent: false, gradesVisibleToStudent: false, submission: null, quiz: null },
    { content: { ...content, id: 3, title: 'Quiz', type: 'Quiz' }, moduleTitle: 'Week 2',
      availableToStudent: true, gradesVisibleToStudent: false, submission: null,
      quiz: { attemptsUsed: 2, latestGradedAttempt: 1, bestScore: .75 } },
  ] }

function open(path = '/courses/1/students') {
  return render(<MemoryRouter initialEntries={[path]}><Routes>
    <Route path="/courses/:courseId/students" element={<StudentDeliveriesPage />} />
    <Route path="/courses/:courseId/modules/:moduleId/contents/:contentId" element={<h1>Activity review</h1>} />
  </Routes></MemoryRouter>)
}

beforeEach(() => {
  vi.resetAllMocks()
  mocks.getCourse.mockResolvedValue({ id: 1, title: 'DAT250' })
  mocks.getCurrentUser.mockResolvedValue({ email: 'teacher@example.com' })
  mocks.getEnrollment.mockResolvedValue({ role: 'TEACHER' })
  mocks.getEnrollments.mockResolvedValue([{ name: 'Alice', email: 'alice@example.com' }, { name: 'Bob', email: 'bob@example.com' }])
  mocks.getStudentDeliveries.mockResolvedValue(snapshot)
})

describe('Student deliveries', () => {
  it('shows missing work, late hand-ins, quiz scores, downloads and grading controls', async () => {
    const user = userEvent.setup()
    open()
    expect(screen.getByText('Loading students…')).toBeInTheDocument()
    expect(await screen.findByText('Missing submission')).toBeInTheDocument()
    expect(screen.getByText('Awaiting grading · Submitted late')).toBeInTheDocument()
    expect(screen.getByText('Grading pending')).toBeInTheDocument()
    expect(screen.getByText(/Best score: 75%/)).toBeInTheDocument()
    expect(screen.getByText('Grade not released to student')).toBeInTheDocument()
    await user.click(screen.getByText('Review and grade delivery'))
    expect(screen.getByRole('link', { name: 'Download essay.pdf' })).toHaveAttribute('href', '/api/submissions/10/file')
    expect(screen.getByText('Alice essay')).toBeInTheDocument()
    await user.type(screen.getByRole('spinbutton', { name: /Grade/ }), '85')
    await user.type(screen.getByLabelText('Feedback'), 'Good work')
    mocks.grade.mockResolvedValue({})
    mocks.getStudentDeliveries.mockResolvedValue({ ...snapshot, deliveries: [{ ...snapshot.deliveries[0],
      submission: { ...submission, grade: 85, feedback: 'Good work', gradedAt: '2026-12-01T10:00:00' } }] })
    await user.click(screen.getByRole('button', { name: 'Submit grade' }))
    expect(mocks.grade).toHaveBeenCalledWith(10, { grade: 85, feedback: 'Good work' }, {})
    expect(await screen.findByText('Graded · Submitted late')).toBeInTheDocument()
  })

  it('switches students without showing the previous student’s deliveries', async () => {
    const user = userEvent.setup()
    open()
    await screen.findByRole('heading', { name: 'Alice' })
    mocks.getStudentDeliveries.mockResolvedValue({ ...snapshot, studentName: 'Bob', studentEmail: 'bob@example.com', deliveries: [] })
    await user.selectOptions(screen.getByLabelText('Student'), 'bob@example.com')
    expect(await screen.findByRole('heading', { name: 'Bob' })).toBeInTheDocument()
    expect(screen.queryByText('Alice essay')).not.toBeInTheDocument()
    expect(screen.getByText('No assessable activities yet')).toBeInTheDocument()
    expect(mocks.getStudentDeliveries).toHaveBeenLastCalledWith(1, 'bob@example.com', expect.anything())
  })

  it('supports direct links to a selected student', async () => {
    mocks.getStudentDeliveries.mockResolvedValue({ ...snapshot, studentName: 'Bob', studentEmail: 'bob@example.com', deliveries: [] })
    open('/courses/1/students?student=bob%40example.com')
    expect(await screen.findByRole('heading', { name: 'Bob' })).toBeInTheDocument()
    expect(screen.getByLabelText('Student')).toHaveValue('bob@example.com')
  })

  it('handles no enrolled students and refuses a student viewer', async () => {
    mocks.getEnrollments.mockResolvedValue([])
    const first = open()
    expect(await screen.findByText('No students enrolled')).toBeInTheDocument()
    first.unmount()
    mocks.getEnrollment.mockResolvedValue({ role: 'STUDENT' })
    open()
    expect(await screen.findByRole('alert')).toHaveTextContent('permission')
    expect(mocks.getStudentDeliveries).not.toHaveBeenCalled()
  })

  it('handles API errors with retry and links to the existing activity review', async () => {
    const user = userEvent.setup()
    mocks.getStudentDeliveries.mockRejectedValueOnce(new ApiError(503, 'Temporarily unavailable', null)).mockResolvedValue(snapshot)
    open()
    expect(await screen.findByRole('alert')).toHaveTextContent('Temporarily unavailable')
    await user.click(screen.getByRole('button', { name: 'Try again' }))
    await user.click(await screen.findByRole('link', { name: 'Essay' }))
    expect(screen.getByRole('heading', { name: 'Activity review' })).toBeInTheDocument()
  })

  it('does not fetch deliveries for a student absent from the current roster', async () => {
    open('/courses/1/students?student=removed%40example.com')
    expect(await screen.findByRole('alert')).toHaveTextContent('no longer enrolled')
    expect(mocks.getStudentDeliveries).not.toHaveBeenCalled()
  })

  it('aborts the previous student request when the selection changes', async () => {
    const user = userEvent.setup()
    let oldSignal: AbortSignal | undefined
    mocks.getStudentDeliveries.mockImplementationOnce((_id, _email, options) => {
      oldSignal = options.signal
      return new Promise(() => {})
    }).mockResolvedValue({ ...snapshot, studentName: 'Bob', deliveries: [] })
    open()
    await screen.findByText('Loading student deliveries…')
    await user.selectOptions(screen.getByLabelText('Student'), 'bob@example.com')
    await waitFor(() => expect(oldSignal?.aborted).toBe(true))
    expect(await screen.findByRole('heading', { name: 'Bob' })).toBeInTheDocument()
  })
})
