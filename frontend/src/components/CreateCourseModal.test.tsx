import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../services/apiClient'
import CreateCourseModal from './CreateCourseModal'

const createCourse = vi.hoisted(() => vi.fn())
vi.mock('../services', () => ({ courses: { createCourse } }))

describe('CreateCourseModal', () => {
  beforeEach(() => {
    createCourse.mockReset()
  })

  it('creates a course with the entered fields', async () => {
    const user = userEvent.setup()
    const onCreated = vi.fn()
    const created = { id: 1, title: 'DAT250', description: 'Advanced topics', term: 'AUTUMN', year: 2026 }
    createCourse.mockResolvedValue(created)

    render(<CreateCourseModal open={true} onClose={vi.fn()} onCreated={onCreated} />)

    await user.type(screen.getByLabelText(/Title/), 'DAT250')
    await user.type(screen.getByLabelText('Description'), 'Advanced topics')
    await user.clear(screen.getByLabelText(/Year/))
    await user.type(screen.getByLabelText(/Year/), '2026')
    await user.click(screen.getByRole('button', { name: 'Create course' }))

    expect(createCourse).toHaveBeenCalledWith(
      { title: 'DAT250', description: 'Advanced topics', term: 'AUTUMN', year: 2026 }, {},
    )
    expect(onCreated).toHaveBeenCalledWith(created)
  })

  it('shows an error when creation fails', async () => {
    const user = userEvent.setup()
    createCourse.mockRejectedValue(new ApiError(400, 'Title must not be blank', null))

    render(<CreateCourseModal open={true} onClose={vi.fn()} onCreated={vi.fn()} />)
    await user.type(screen.getByLabelText(/Title/), 'DAT250')
    await user.click(screen.getByRole('button', { name: 'Create course' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Title must not be blank')
  })

  it('does not render its form when closed', () => {
    render(<CreateCourseModal open={false} onClose={vi.fn()} onCreated={vi.fn()} />)

    expect(screen.queryByLabelText('Title')).not.toBeInTheDocument()
  })
})
