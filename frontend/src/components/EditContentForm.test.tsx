import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../services/apiClient'
import EditContentForm from './EditContentForm'
import type { ContentResponse } from '../types/content'

const updateContent = vi.hoisted(() => vi.fn())
const updateText = vi.hoisted(() => vi.fn())
vi.mock('../services', () => ({ contents: { updateContent, updateText } }))

const page: ContentResponse = {
  id: 7, moduleId: 1, type: 'StaticPage', title: 'Syllabus', position: 2,
  visibleFrom: '2026-12-01T08:00:00', visibleUntil: null, body: 'Old body', instructions: null,
  repositoryUrl: null, deadline: null, lateSubmissionsAccepted: null, groupSubmission: null,
}

describe('EditContentForm', () => {
  beforeEach(() => {
    updateContent.mockReset().mockResolvedValue(page)
    updateText.mockReset().mockResolvedValue(page)
  })

  it('saves the title, body and visibility window, keeping the position', async () => {
    const onSaved = vi.fn()
    const user = userEvent.setup()
    render(<EditContentForm content={page} onSaved={onSaved} onCancel={vi.fn()} />)

    expect(screen.getByLabelText('Visible from (Europe/Oslo)')).toHaveValue('2026-12-01T08:00')
    await user.clear(screen.getByLabelText(/Title/))
    await user.type(screen.getByLabelText(/Title/), 'Course syllabus')
    await user.clear(screen.getByLabelText(/Body/))
    await user.type(screen.getByLabelText(/Body/), 'New body')
    fireEvent.change(screen.getByLabelText('Visible until (Europe/Oslo)'), { target: { value: '2026-12-20T16:00' } })
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(updateContent).toHaveBeenCalledWith(7, {
      title: 'Course syllabus', position: 2,
      visibleFrom: '2026-12-01T08:00', visibleUntil: '2026-12-20T16:00',
    }, {})
    expect(updateText).toHaveBeenCalledWith(7, 'New body', {})
    expect(onSaved).toHaveBeenCalled()
  })

  it('does not resend the text when only the title changed, and clears a window', async () => {
    const user = userEvent.setup()
    render(<EditContentForm content={page} onSaved={vi.fn()} onCancel={vi.fn()} />)

    await user.type(screen.getByLabelText(/Title/), '!')
    fireEvent.change(screen.getByLabelText('Visible from (Europe/Oslo)'), { target: { value: '' } })
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(updateContent).toHaveBeenCalledWith(7, expect.objectContaining({ title: 'Syllabus!', visibleFrom: null }), {})
    expect(updateText).not.toHaveBeenCalled()
  })

  it('edits instructions for an assignment and the URL for a coding exercise, nothing for a quiz', () => {
    const { unmount } = render(<EditContentForm content={{ ...page, type: 'Assignment', instructions: 'Write' }}
      onSaved={vi.fn()} onCancel={vi.fn()} />)
    expect(screen.getByLabelText(/Instructions/)).toHaveValue('Write')
    unmount()

    const second = render(<EditContentForm content={{ ...page, type: 'CodingExercise', repositoryUrl: 'https://x.test/r' }}
      onSaved={vi.fn()} onCancel={vi.fn()} />)
    expect(screen.getByLabelText(/Repository URL/)).toHaveValue('https://x.test/r')
    second.unmount()

    render(<EditContentForm content={{ ...page, type: 'Quiz' }} onSaved={vi.fn()} onCancel={vi.fn()} />)
    expect(screen.queryByLabelText(/Body|Instructions|Repository URL/)).not.toBeInTheDocument()
  })

  it('rejects a window that ends before it starts', async () => {
    const user = userEvent.setup()
    render(<EditContentForm content={page} onSaved={vi.fn()} onCancel={vi.fn()} />)

    fireEvent.change(screen.getByLabelText('Visible until (Europe/Oslo)'), { target: { value: '2026-11-01T08:00' } })
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(screen.getByText('"Visible until" must be after "Visible from".')).toBeInTheDocument()
    expect(updateContent).not.toHaveBeenCalled()
  })

  it('shows the server error and stays open', async () => {
    updateContent.mockRejectedValue(new ApiError(400, 'Title must not be blank', null))
    const onSaved = vi.fn()
    const user = userEvent.setup()
    render(<EditContentForm content={page} onSaved={onSaved} onCancel={vi.fn()} />)

    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(await screen.findByText('Title must not be blank')).toBeInTheDocument()
    expect(onSaved).not.toHaveBeenCalled()
  })
})
