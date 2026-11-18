import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../services/apiClient'
import ManageEnrollmentsModal from './ManageEnrollmentsModal'

const getEnrollments = vi.hoisted(() => vi.fn())
const enroll = vi.hoisted(() => vi.fn())
const unenroll = vi.hoisted(() => vi.fn())
vi.mock('../services', () => ({ enrollments: { getEnrollments, enroll, unenroll } }))

const teacher = {
  id: 1, courseId: 1, courseTitle: 'DAT250', email: 'teacher@example.com', name: 'Alice Teacherson',
  role: 'TEACHER', enrolledAt: '2026-01-01T00:00:00',
}
const student = {
  id: 2, courseId: 1, courseTitle: 'DAT250', email: 'student@example.com', name: 'Bob Studentson',
  role: 'STUDENT', enrolledAt: '2026-01-02T00:00:00',
}

describe('ManageEnrollmentsModal', () => {
  beforeEach(() => {
    getEnrollments.mockReset()
    enroll.mockReset()
    unenroll.mockReset()
  })

  it('loads and shows the roster when opened', async () => {
    getEnrollments.mockResolvedValue([teacher, student])
    render(<ManageEnrollmentsModal courseId={1} open={true} onClose={vi.fn()} />)

    expect(await screen.findByText('teacher@example.com')).toBeInTheDocument()
    expect(screen.getByText('student@example.com')).toBeInTheDocument()
    expect(screen.getByText('Teacher')).toBeInTheDocument()
    expect(screen.getByText('Student')).toBeInTheDocument()
  })

  it('does not load the roster when closed', () => {
    render(<ManageEnrollmentsModal courseId={1} open={false} onClose={vi.fn()} />)

    expect(getEnrollments).not.toHaveBeenCalled()
  })

  it('shows an empty state for a course with no members', async () => {
    getEnrollments.mockResolvedValue([])
    render(<ManageEnrollmentsModal courseId={1} open={true} onClose={vi.fn()} />)

    expect(await screen.findByText('No one is enrolled yet.')).toBeInTheDocument()
  })

  it('enrolls a student by email and refreshes the roster', async () => {
    const user = userEvent.setup()
    getEnrollments.mockResolvedValueOnce([teacher]).mockResolvedValueOnce([teacher, student])
    enroll.mockResolvedValue(student)
    render(<ManageEnrollmentsModal courseId={1} open={true} onClose={vi.fn()} />)

    expect(await screen.findByText('teacher@example.com')).toBeInTheDocument()
    await user.type(screen.getByLabelText(/Student email/), 'student@example.com')
    await user.click(screen.getByRole('button', { name: 'Enroll student' }))

    expect(enroll).toHaveBeenCalledWith(1, { email: 'student@example.com', role: 'STUDENT' }, {})
    expect(await screen.findByText('student@example.com')).toBeInTheDocument()
  })

  it('shows an error when enrolling an unknown email', async () => {
    const user = userEvent.setup()
    getEnrollments.mockResolvedValue([teacher])
    enroll.mockRejectedValue(new ApiError(404, 'User not found: nobody@example.com', null))
    render(<ManageEnrollmentsModal courseId={1} open={true} onClose={vi.fn()} />)

    expect(await screen.findByText('teacher@example.com')).toBeInTheDocument()
    await user.type(screen.getByLabelText(/Student email/), 'nobody@example.com')
    await user.click(screen.getByRole('button', { name: 'Enroll student' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('User not found')
  })

  it('removes a member and refreshes the roster', async () => {
    const user = userEvent.setup()
    getEnrollments.mockResolvedValueOnce([teacher, student]).mockResolvedValueOnce([teacher])
    unenroll.mockResolvedValue(undefined)
    render(<ManageEnrollmentsModal courseId={1} open={true} onClose={vi.fn()} />)

    expect(await screen.findByText('student@example.com')).toBeInTheDocument()
    const removeButtons = screen.getAllByRole('button', { name: 'Remove' })
    await user.click(removeButtons[removeButtons.length - 1])

    expect(unenroll).toHaveBeenCalledWith(1, 'student@example.com', {})
    expect(await screen.findAllByRole('button', { name: 'Remove' })).toHaveLength(1)
    expect(screen.queryByText('student@example.com')).not.toBeInTheDocument()
  })
})
