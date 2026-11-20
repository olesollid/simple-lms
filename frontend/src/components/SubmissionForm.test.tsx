import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../services/apiClient'
import SubmissionForm from './SubmissionForm'

const submit = vi.hoisted(() => vi.fn())
const upload = vi.hoisted(() => vi.fn())
vi.mock('../services', () => ({ submissions: { submit, upload } }))

describe('SubmissionForm', () => {
  it('shows a waiting message without grade or feedback while results are private', () => {
    render(<SubmissionForm contentId={1} onSubmitted={() => {}} existingSubmission={{
      id: 1, contentId: 1, submitterEmail: 'student@example.com', groupId: null,
      lastSubmittedByEmail: 'student@example.com', text: 'My essay',
      submittedAt: '2026-01-01T10:00:00', grade: null, feedback: null,
      gradedAt: null, gradingComplete: true,
    }} />)
    expect(screen.getByText(/Results will appear when your teacher publishes them/)).toBeInTheDocument()
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
    expect(screen.queryByText('Grade:')).not.toBeInTheDocument()
  })
  beforeEach(() => {
    submit.mockReset()
    upload.mockReset()
  })

  it('submits the entered text', async () => {
    const user = userEvent.setup()
    const onSubmitted = vi.fn()
    submit.mockResolvedValue({})
    render(<SubmissionForm contentId={1} existingSubmission={null} onSubmitted={onSubmitted} />)

    await user.type(screen.getByLabelText('Your submission'), 'My essay')
    await user.click(screen.getByRole('button', { name: 'Submit' }))

    expect(submit).toHaveBeenCalledWith(1, { text: 'My essay' }, {})
    expect(onSubmitted).toHaveBeenCalledTimes(1)
  })

  it('uploads a document without requiring text', async () => {
    const user = userEvent.setup()
    const onSubmitted = vi.fn()
    upload.mockResolvedValue({})
    render(<SubmissionForm contentId={1} existingSubmission={null} onSubmitted={onSubmitted} />)
    const file = new File(['%PDF-1.4'], 'essay.pdf', { type: 'application/pdf' })
    await user.upload(screen.getByLabelText('Attach a document'), file)
    await user.click(screen.getByRole('button', { name: 'Submit' }))
    expect(upload).toHaveBeenCalledWith(1, '', file, {})
    expect(onSubmitted).toHaveBeenCalledOnce()
    expect(submit).not.toHaveBeenCalled()
  })

  it('rejects an oversized document before sending it', async () => {
    const user = userEvent.setup()
    render(<SubmissionForm contentId={1} existingSubmission={null} onSubmitted={() => {}} />)
    await user.upload(screen.getByLabelText('Attach a document'),
      new File([new Uint8Array(10 * 1024 * 1024 + 1)], 'large.pdf', { type: 'application/pdf' }))
    await user.click(screen.getByRole('button', { name: 'Submit' }))
    expect(screen.getByRole('alert')).toHaveTextContent('up to 10 MB')
    expect(upload).not.toHaveBeenCalled()
  })

  it('shows an error without calling onSubmitted when the submission fails', async () => {
    const user = userEvent.setup()
    const onSubmitted = vi.fn()
    submit.mockRejectedValue(new ApiError(409, 'Already graded', null))
    render(<SubmissionForm contentId={1} existingSubmission={null} onSubmitted={onSubmitted} />)

    await user.type(screen.getByLabelText('Your submission'), 'My essay')
    await user.click(screen.getByRole('button', { name: 'Submit' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/already been graded/i)
    expect(onSubmitted).not.toHaveBeenCalled()
  })

  it('shows the grade and feedback for a graded submission instead of a form', () => {
    const existing = {
      id: 1, contentId: 1, submitterEmail: 'student@example.com', groupId: null,
      lastSubmittedByEmail: 'student@example.com', text: 'My essay',
      submittedAt: '2026-01-01T10:00:00', grade: 85.5, feedback: 'Well done',
      gradedAt: '2026-01-02T10:00:00',
    }
    render(<SubmissionForm contentId={1} existingSubmission={existing} onSubmitted={vi.fn()} />)

    expect(screen.getByText(/85.5/)).toBeInTheDocument()
    expect(screen.getByText(/Well done/)).toBeInTheDocument()
    expect(screen.queryByLabelText('Your submission')).not.toBeInTheDocument()
  })

  it('shows an update form for an ungraded existing submission', () => {
    const existing = {
      id: 1, contentId: 1, submitterEmail: 'student@example.com', groupId: null,
      lastSubmittedByEmail: 'student@example.com', text: 'Draft',
      submittedAt: '2026-01-01T10:00:00', grade: null, feedback: null, gradedAt: null,
    }
    render(<SubmissionForm contentId={1} existingSubmission={existing} onSubmitted={vi.fn()} />)

    expect(screen.getByLabelText('Update your submission')).toHaveValue('Draft')
    expect(screen.getByRole('button', { name: 'Resubmit' })).toBeInTheDocument()
  })

  it("re-seeds the textarea when a teammate's newer submission arrives", () => {
    const first = {
      id: 1, contentId: 1, submitterEmail: 'alice@example.com', groupId: null,
      lastSubmittedByEmail: 'alice@example.com', text: "Alice's draft",
      submittedAt: '2026-01-01T10:00:00', grade: null, feedback: null, gradedAt: null,
    }
    const { rerender } = render(
      <SubmissionForm contentId={1} existingSubmission={first} onSubmitted={vi.fn()} />,
    )
    expect(screen.getByLabelText('Update your submission')).toHaveValue("Alice's draft")

    const second = { ...first, text: "Bob's newer version", submittedAt: '2026-01-02T10:00:00' }
    rerender(<SubmissionForm contentId={1} existingSubmission={second} onSubmitted={vi.fn()} />)

    expect(screen.getByLabelText('Update your submission')).toHaveValue("Bob's newer version")
  })

  it('does not clobber in-progress typing on an unrelated re-render', async () => {
    const user = userEvent.setup()
    const existing = {
      id: 1, contentId: 1, submitterEmail: 'alice@example.com', groupId: null,
      lastSubmittedByEmail: 'alice@example.com', text: "Alice's draft",
      submittedAt: '2026-01-01T10:00:00', grade: null, feedback: null, gradedAt: null,
    }
    const { rerender } = render(
      <SubmissionForm contentId={1} existingSubmission={existing} onSubmitted={vi.fn()} />,
    )
    await user.type(screen.getByLabelText('Update your submission'), ' plus more')

    // A new object with the SAME submittedAt (e.g. a re-render caused by an unrelated parent
    // state change, not a genuinely new submission) must not overwrite the edit in progress.
    rerender(<SubmissionForm contentId={1} existingSubmission={{ ...existing }} onSubmitted={vi.fn()} />)

    expect(screen.getByLabelText('Update your submission')).toHaveValue("Alice's draft plus more")
  })
})
