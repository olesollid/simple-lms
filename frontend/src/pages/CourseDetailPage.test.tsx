import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../services/apiClient'
import CourseDetailPage from './CourseDetailPage'

const getCourse = vi.hoisted(() => vi.fn())
const getModules = vi.hoisted(() => vi.fn())
vi.mock('../services', () => ({ courses: { getCourse }, modules: { getModules } }))

const course = { id: 1, title: 'DAT250', description: 'Software technology', term: 'AUTUMN', year: 2026 }
const makeModule = (id: number) => ({ id, courseId: 1, title: `Module ${id}`, dependencyIds: [] })

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/courses/:id" element={<CourseDetailPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('CourseDetailPage', () => {
  beforeEach(() => {
    getCourse.mockReset()
    getModules.mockReset()
  })

  it('shows the course and its modules', async () => {
    getCourse.mockResolvedValue(course)
    getModules.mockResolvedValue([makeModule(1), makeModule(2)])
    renderAt('/courses/1')

    expect(await screen.findByRole('heading', { level: 1, name: 'DAT250' })).toBeInTheDocument()
    expect(screen.getByText('Software technology')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Module 1' })).toBeInTheDocument()
    expect(getCourse).toHaveBeenCalledWith(1, expect.anything())
    expect(getModules).toHaveBeenCalledWith(1, expect.anything())
  })

  it('shows an empty state when the course has no modules', async () => {
    getCourse.mockResolvedValue(course)
    getModules.mockResolvedValue([])
    renderAt('/courses/1')

    expect(await screen.findByRole('heading', { name: 'No modules yet' })).toBeInTheDocument()
  })

  it('shows an error and recovers on retry', async () => {
    const user = userEvent.setup()
    getCourse.mockRejectedValueOnce(new ApiError(403, 'Forbidden', null))
    getModules.mockResolvedValue([])
    renderAt('/courses/1')

    expect(await screen.findByRole('alert')).toHaveTextContent(/permission/i)

    getCourse.mockResolvedValueOnce(course)
    await user.click(screen.getByRole('button', { name: 'Try again' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'DAT250' })).toBeInTheDocument()
    expect(getCourse).toHaveBeenCalledTimes(2)
  })

  it('shows an error for an invalid course id', async () => {
    renderAt('/courses/not-a-number')

    expect(await screen.findByRole('alert')).toHaveTextContent('could not be found')
    expect(getCourse).not.toHaveBeenCalled()
  })
})
