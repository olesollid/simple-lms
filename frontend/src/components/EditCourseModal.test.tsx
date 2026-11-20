import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../services/apiClient'
import EditCourseModal from './EditCourseModal'

const updateCourse = vi.hoisted(() => vi.fn())
vi.mock('../services', () => ({ courses: { updateCourse } }))

const course = { id: 7, title: 'DAT250', description: 'Advanced topics', term: 'AUTUMN' as const, year: 2026 }

describe('EditCourseModal', () => {
  beforeEach(() => {
    updateCourse.mockReset()
  })

  it('prefills the form with the course values', () => {
    render(<EditCourseModal course={course} open={true} onClose={vi.fn()} onSaved={vi.fn()} />)

    expect(screen.getByLabelText(/Title/)).toHaveValue('DAT250')
    expect(screen.getByLabelText('Description')).toHaveValue('Advanced topics')
    expect(screen.getByLabelText(/Year/)).toHaveValue(2026)
  })

  it('saves the edited course', async () => {
    const user = userEvent.setup()
    const onSaved = vi.fn()
    const saved = { ...course, title: 'DAT250 v2' }
    updateCourse.mockResolvedValue(saved)

    render(<EditCourseModal course={course} open={true} onClose={vi.fn()} onSaved={onSaved} />)
    await user.clear(screen.getByLabelText(/Title/))
    await user.type(screen.getByLabelText(/Title/), 'DAT250 v2')
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(updateCourse).toHaveBeenCalledWith(
      7, { title: 'DAT250 v2', description: 'Advanced topics', term: 'AUTUMN', year: 2026 }, {},
    )
    expect(onSaved).toHaveBeenCalledWith(saved)
  })

  it('shows an error when saving fails', async () => {
    const user = userEvent.setup()
    updateCourse.mockRejectedValue(new ApiError(403, 'Only teachers can edit', null))

    render(<EditCourseModal course={course} open={true} onClose={vi.fn()} onSaved={vi.fn()} />)
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Only teachers can edit')
  })
})
