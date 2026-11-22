import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ProfilePage from './ProfilePage'
import { ApiError } from '../services/apiClient'
import type { MyActivity, MyDeliveriesResponse } from '../types/myDeliveries'

const mocks = vi.hoisted(() => ({ me: vi.fn(), list: vi.fn(), deliveries: vi.fn() }))
vi.mock('../services', () => ({ users: { getCurrentUser: mocks.me }, courses: { getCourses: mocks.list, getMyDeliveries: mocks.deliveries } }))
const course = { id: 1, title: 'DAT250', description: 'Software', term: 'AUTUMN', year: 2026 }
const activity: MyActivity = { contentId: 1, moduleId: 2, title: 'Essay', type: 'Assignment',
  deadline: null, status: 'GRADED', grade: 87, feedback: 'Good reasoning', lateSubmission: false,
  groupSubmission: false, needsGroup: false, gradingPending: false, overdue: false }
const snapshot: MyDeliveriesResponse = { courseId: 1, role: 'STUDENT', activities: [activity,
  { ...activity, contentId: 2, title: 'Group report', status: 'AWAITING_PUBLICATION', grade: null, feedback: null, groupSubmission: true },
  { ...activity, contentId: 3, title: 'Code report', type: 'CodingExercise', status: 'NOT_SUBMITTED', grade: null, feedback: null,
    deadline: '2026-11-30T12:00:00', overdue: true },
] }
const open = () => render(<MemoryRouter><ProfilePage /></MemoryRouter>)
beforeEach(() => {
  vi.resetAllMocks()
  mocks.me.mockResolvedValue({ name: 'Alice Student', email: 'alice@example.com' })
  mocks.list.mockResolvedValue([course])
  mocks.deliveries.mockResolvedValue(snapshot)
})

describe('Profile', () => {
  it('automatically shows personal information and separates published, submitted and missing work', async () => {
    open()
    expect(screen.getByText('Loading profile…')).toBeInTheDocument()
    expect(await screen.findByText('Alice Student')).toBeInTheDocument()
    expect(screen.getByText('alice@example.com')).toBeInTheDocument()
    expect(await screen.findByText('87 / 100')).toBeInTheDocument()
    const grades = screen.getByRole('region', { name: 'Published grades' })
    expect(within(grades).getByText('Good reasoning')).toBeInTheDocument()
    const submitted = screen.getByRole('region', { name: 'Submitted work' })
    expect(within(submitted).getByText('Awaiting publication')).toBeInTheDocument()
    expect(within(submitted).getByText(/Group hand-in/)).toBeInTheDocument()
    expect(within(submitted).queryByText(/87/)).not.toBeInTheDocument()
    const missing = screen.getByRole('region', { name: 'Not submitted' })
    expect(within(missing).getByText('Not submitted · Overdue')).toBeInTheDocument()
    expect(within(missing).getByRole('link', { name: 'Code report' })).toHaveAttribute('href', '/courses/1/modules/2/contents/3')
  })

  it('keeps a course failure separate from personal information and other courses, and supports retry', async () => {
    const user = userEvent.setup()
    mocks.list.mockResolvedValue([course, { ...course, id: 2, title: 'INF100' }])
    mocks.deliveries.mockImplementation((id: number) => id === 1
      ? Promise.resolve(snapshot) : Promise.reject(new Error('offline')))
    open()
    expect(await screen.findByText('Alice Student')).toBeInTheDocument()
    expect(await screen.findByText('Good reasoning')).toBeInTheDocument()
    expect(await screen.findByRole('alert')).toHaveTextContent('Check your connection')
    mocks.deliveries.mockResolvedValue({ courseId: 2, role: 'TEACHER', activities: [] })
    await user.click(screen.getByRole('button', { name: 'Retry INF100' }))
    expect(await screen.findByText('Teacher')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Student deliveries' })).toHaveAttribute('href', '/courses/2/students')
    expect(screen.getByText('Good reasoning')).toBeInTheDocument()
  })

  it('handles no courses and unauthenticated sessions', async () => {
    mocks.list.mockResolvedValue([])
    const first = open()
    expect(await screen.findByText('You are not enrolled in any courses yet.')).toBeInTheDocument()
    first.unmount()
    mocks.me.mockRejectedValue(new ApiError(401, 'Unauthorized', null))
    open()
    expect(await screen.findByRole('alert')).toHaveTextContent('Please sign in')
  })

  it('supports a course without available activities', async () => {
    mocks.deliveries.mockResolvedValue({ courseId: 1, role: 'STUDENT', activities: [] })
    open()
    expect(await screen.findByText('No available assignments or quizzes yet.')).toBeInTheDocument()
  })

  it('refreshes the profile to retrieve newly published grades', async () => {
    const user = userEvent.setup()
    mocks.deliveries.mockResolvedValue({ ...snapshot, activities: [snapshot.activities[1]] })
    open()
    expect(await screen.findByText('Awaiting publication')).toBeInTheDocument()
    mocks.deliveries.mockResolvedValue({ ...snapshot, activities: [{ ...activity, title: 'Group report', groupSubmission: true }] })
    await user.click(screen.getByRole('button', { name: 'Refresh profile' }))
    expect(await screen.findByText('87 / 100')).toBeInTheDocument()
    expect(screen.queryByText('Awaiting publication')).not.toBeInTheDocument()
  })
})
