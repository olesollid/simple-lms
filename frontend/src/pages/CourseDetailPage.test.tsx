import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../services/apiClient'
import CourseDetailPage from './CourseDetailPage'

const getCourse = vi.hoisted(() => vi.fn())
const deleteCourse = vi.hoisted(() => vi.fn())
const getModules = vi.hoisted(() => vi.fn())
const deleteModule = vi.hoisted(() => vi.fn())
const getContentsByModule = vi.hoisted(() => vi.fn())
const getCurrentUser = vi.hoisted(() => vi.fn())
const getEnrollment = vi.hoisted(() => vi.fn())
const getEnrollments = vi.hoisted(() => vi.fn())
vi.mock('../services', () => ({
  courses: { getCourse, deleteCourse },
  modules: { getModules, deleteModule },
  contents: { getContentsByModule },
  users: { getCurrentUser },
  enrollments: { getEnrollment, getEnrollments },
}))

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
    deleteCourse.mockReset()
    getModules.mockReset()
    deleteModule.mockReset()
    getContentsByModule.mockReset()
    getContentsByModule.mockResolvedValue([])
    getCurrentUser.mockReset()
    getCurrentUser.mockResolvedValue({ email: 'student@example.com', name: 'Student' })
    getEnrollment.mockReset()
    getEnrollment.mockResolvedValue({ role: 'STUDENT' })
    getEnrollments.mockReset()
    getEnrollments.mockResolvedValue([])
  })

  it('shows the course and its modules', async () => {
    getCourse.mockResolvedValue(course)
    getModules.mockResolvedValue([makeModule(1), makeModule(2)])
    renderAt('/courses/1')

    expect(await screen.findByRole('heading', { level: 1, name: /DAT250/ })).toBeInTheDocument()
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

    expect(await screen.findByRole('heading', { level: 1, name: /DAT250/ })).toBeInTheDocument()
    expect(getCourse).toHaveBeenCalledTimes(2)
  })

  it('shows an error for an invalid course id', async () => {
    renderAt('/courses/not-a-number')

    expect(await screen.findByRole('alert')).toHaveTextContent('could not be found')
    expect(getCourse).not.toHaveBeenCalled()
  })

  it('shows an "Add module" button for a teacher', async () => {
    getCourse.mockResolvedValue(course)
    getModules.mockResolvedValue([])
    getEnrollment.mockResolvedValue({ role: 'TEACHER' })
    renderAt('/courses/1')

    expect(await screen.findByRole('button', { name: 'Add module' })).toBeInTheDocument()
  })

  it('does not show an "Add module" button for a student', async () => {
    getCourse.mockResolvedValue(course)
    getModules.mockResolvedValue([])
    getEnrollment.mockResolvedValue({ role: 'STUDENT' })
    renderAt('/courses/1')

    expect(await screen.findByRole('heading', { name: 'No modules yet' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Add module' })).not.toBeInTheDocument()
  })

  it('shows a "Manage students" button for a teacher that opens the roster', async () => {
    const user = userEvent.setup()
    getCourse.mockResolvedValue(course)
    getModules.mockResolvedValue([])
    getEnrollment.mockResolvedValue({ role: 'TEACHER' })
    renderAt('/courses/1')

    await user.click(await screen.findByRole('button', { name: 'Manage students' }))

    expect(await screen.findByRole('heading', { name: 'Manage students' })).toBeInTheDocument()
    expect(getEnrollments).toHaveBeenCalledWith(1, undefined, expect.anything())
  })

  it('does not show a "Manage students" button for a student', async () => {
    getCourse.mockResolvedValue(course)
    getModules.mockResolvedValue([])
    getEnrollment.mockResolvedValue({ role: 'STUDENT' })
    renderAt('/courses/1')

    expect(await screen.findByRole('heading', { name: 'No modules yet' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Manage students' })).not.toBeInTheDocument()
  })

  it('does not show a "Delete course" button for a student', async () => {
    getCourse.mockResolvedValue(course)
    getModules.mockResolvedValue([])
    getEnrollment.mockResolvedValue({ role: 'STUDENT' })
    renderAt('/courses/1')

    expect(await screen.findByRole('heading', { name: 'No modules yet' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Delete course' })).not.toBeInTheDocument()
  })

  describe('as a teacher deleting the course', () => {
    const confirmSpy = vi.spyOn(window, 'confirm')

    afterEach(() => confirmSpy.mockReset())

    it('deletes the course and navigates back to the catalogue', async () => {
      const user = userEvent.setup()
      confirmSpy.mockReturnValue(true)
      getCourse.mockResolvedValue(course)
      getModules.mockResolvedValue([])
      getEnrollment.mockResolvedValue({ role: 'TEACHER' })
      deleteCourse.mockResolvedValue(undefined)
      renderAt('/courses/1')

      await user.click(await screen.findByRole('button', { name: 'Delete course' }))

      expect(deleteCourse).toHaveBeenCalledWith(1, {})
    })

    it('shows an error when the course has dependents', async () => {
      const user = userEvent.setup()
      confirmSpy.mockReturnValue(true)
      getCourse.mockResolvedValue(course)
      getModules.mockResolvedValue([])
      getEnrollment.mockResolvedValue({ role: 'TEACHER' })
      deleteCourse.mockRejectedValue(
        new ApiError(409, 'Course 1 has modules or enrollments and cannot be deleted until they are removed', null),
      )
      renderAt('/courses/1')

      await user.click(await screen.findByRole('button', { name: 'Delete course' }))

      expect(await screen.findByRole('alert')).toHaveTextContent('has modules or enrollments')
    })
  })
})
