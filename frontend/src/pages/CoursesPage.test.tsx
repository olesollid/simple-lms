import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../services/apiClient'
import CoursesPage from './CoursesPage'

const getCourses = vi.hoisted(() => vi.fn())
vi.mock('../services', () => ({ courses: { getCourses } }))

const course = { id: 1, title: 'DAT250', description: 'Software technology', term: 'AUTUMN', year: 2026 }

describe('CoursesPage', () => {
  beforeEach(() => getCourses.mockReset())

  it('shows the heading and loads courses', async () => {
    getCourses.mockResolvedValue([course])
    render(<MemoryRouter><CoursesPage /></MemoryRouter>)

    expect(screen.getByRole('heading', { level: 1, name: 'Courses' })).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: 'DAT250' })).toBeInTheDocument()
  })

  it('shows an error and recovers on retry', async () => {
    const user = userEvent.setup()
    getCourses.mockRejectedValueOnce(new ApiError(403, 'Forbidden', null))
    render(<MemoryRouter><CoursesPage /></MemoryRouter>)

    expect(await screen.findByRole('alert')).toHaveTextContent(/permission/i)

    getCourses.mockResolvedValueOnce([course])
    await user.click(screen.getByRole('button', { name: 'Try again' }))

    expect(await screen.findByRole('heading', { name: 'DAT250' })).toBeInTheDocument()
    expect(getCourses).toHaveBeenCalledTimes(2)
  })
})
