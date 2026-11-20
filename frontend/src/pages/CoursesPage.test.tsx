import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../services/apiClient'
import CoursesPage from './CoursesPage'

const getCourses = vi.hoisted(() => vi.fn())
const getCurrentUser = vi.hoisted(() => vi.fn())
const getEnrollment = vi.hoisted(() => vi.fn())
vi.mock('../services', () => ({
  courses: { getCourses },
  users: { getCurrentUser },
  enrollments: { getEnrollment },
}))

const course = { id: 1, title: 'DAT250', description: 'Software technology', term: 'AUTUMN', year: 2026 }

describe('CoursesPage', () => {
  beforeEach(() => {
    getCourses.mockReset()
    getCurrentUser.mockReset()
    getCurrentUser.mockResolvedValue({ email: 'student@example.com', name: 'Student' })
    getEnrollment.mockReset()
    getEnrollment.mockResolvedValue({ role: 'STUDENT' })
  })

  it('shows the heading and loads courses', async () => {
    getCourses.mockResolvedValue([course])
    render(<MemoryRouter><CoursesPage /></MemoryRouter>)

    expect(screen.getByRole('heading', { level: 1, name: 'Courses' })).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: /DAT250/ })).toBeInTheDocument()
  })

  it('shows a role badge for each course', async () => {
    getCourses.mockResolvedValue([course])
    getEnrollment.mockResolvedValue({ role: 'TEACHER' })
    render(<MemoryRouter><CoursesPage /></MemoryRouter>)

    expect(await screen.findByText('Teacher')).toBeInTheDocument()
  })

  it('shows an error and recovers on retry', async () => {
    const user = userEvent.setup()
    getCourses.mockRejectedValueOnce(new ApiError(403, 'Forbidden', null))
    render(<MemoryRouter><CoursesPage /></MemoryRouter>)

    expect(await screen.findByRole('alert')).toHaveTextContent(/permission/i)

    getCourses.mockResolvedValueOnce([course])
    await user.click(screen.getByRole('button', { name: 'Try again' }))

    expect(await screen.findByRole('heading', { name: /DAT250/ })).toBeInTheDocument()
    expect(getCourses).toHaveBeenCalledTimes(2)
  })
})
