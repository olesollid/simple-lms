import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../services/apiClient'
import SubmissionsReview from './SubmissionsReview'

const getSubmissionsByContent = vi.hoisted(() => vi.fn())
const grade = vi.hoisted(() => vi.fn())
vi.mock('../services', () => ({ submissions: { getSubmissionsByContent, grade } }))

const individual = {
  id: 1, contentId: 1, submitterEmail: 'student@example.com', groupId: null,
  lastSubmittedByEmail: 'student@example.com', text: 'My essay',
  submittedAt: '2026-01-01T10:00:00', grade: null, feedback: null, gradedAt: null,
}

const groupSubmission = {
  id: 2, contentId: 1, submitterEmail: null, groupId: 5,
  lastSubmittedByEmail: 'bob@example.com', text: 'Our shared essay',
  submittedAt: '2026-01-02T10:00:00', grade: null, feedback: null, gradedAt: null,
}

const graded = {
  id: 3, contentId: 1, submitterEmail: 'alice@example.com', groupId: null,
  lastSubmittedByEmail: 'alice@example.com', text: 'Graded essay',
  submittedAt: '2026-01-01T10:00:00', grade: 90, feedback: 'Great job', gradedAt: '2026-01-03T10:00:00',
}

describe('SubmissionsReview', () => {
  beforeEach(() => {
    getSubmissionsByContent.mockReset()
    grade.mockReset()
  })

  it('shows a message when there are no submissions', async () => {
    getSubmissionsByContent.mockResolvedValue([])
    render(<SubmissionsReview contentId={1} />)

    expect(await screen.findByText('No submissions yet.')).toBeInTheDocument()
  })

  it('lists individual and group submissions with their identity', async () => {
    getSubmissionsByContent.mockResolvedValue([individual, groupSubmission])
    render(<SubmissionsReview contentId={1} />)

    expect(await screen.findByText('student@example.com')).toBeInTheDocument()
    expect(screen.getByText('Group 5')).toBeInTheDocument()
    expect(screen.getByText(/by bob@example.com/)).toBeInTheDocument()
  })

  it('shows the grade and feedback for an already-graded submission instead of a form', async () => {
    getSubmissionsByContent.mockResolvedValue([graded])
    render(<SubmissionsReview contentId={1} />)

    expect(await screen.findByText(/90/)).toBeInTheDocument()
    expect(screen.getByText(/Great job/)).toBeInTheDocument()
    expect(screen.queryByLabelText('Grade')).not.toBeInTheDocument()
  })

  it('submits a grade and feedback for an ungraded submission', async () => {
    const user = userEvent.setup()
    getSubmissionsByContent.mockResolvedValue([individual])
    grade.mockResolvedValue({})
    render(<SubmissionsReview contentId={1} />)

    await user.type(await screen.findByLabelText('Grade'), '85')
    await user.type(screen.getByLabelText('Feedback'), 'Well done')
    await user.click(screen.getByRole('button', { name: 'Submit grade' }))

    expect(grade).toHaveBeenCalledWith(1, { grade: 85, feedback: 'Well done' }, {})
  })

  it('shows an error when loading submissions fails', async () => {
    getSubmissionsByContent.mockRejectedValue(new ApiError(403, 'Forbidden', null))
    render(<SubmissionsReview contentId={1} />)

    expect(await screen.findByRole('alert')).toHaveTextContent(/permission/i)
  })
})
