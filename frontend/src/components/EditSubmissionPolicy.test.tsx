import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, it, vi } from 'vitest'
import EditSubmissionPolicy from './EditSubmissionPolicy'
import { ApiError } from '../services/apiClient'
import type { ContentResponse } from '../types/content'

const updateSubmissionPolicy = vi.hoisted(() => vi.fn())
vi.mock('../services', () => ({ contents: { updateSubmissionPolicy } }))
const content: ContentResponse = {
  id: 7, moduleId: 1, type: 'Assignment', title: 'Essay', position: 0,
  visibleFrom: null, visibleUntil: null, body: null, instructions: 'Write',
  repositoryUrl: null, deadline: '2026-11-30T12:00:00', lateSubmissionsAccepted: false, groupSubmission: false,
}
beforeEach(() => updateSubmissionPolicy.mockReset())

it('saves an edited deadline and late policy without browser timezone conversion', async () => {
  const user = userEvent.setup()
  const onSaved = vi.fn()
  updateSubmissionPolicy.mockResolvedValue(content)
  render(<EditSubmissionPolicy content={content} onSaved={onSaved} />)
  await user.click(screen.getByRole('button', { name: 'Edit deadline and late policy' }))
  fireEvent.change(screen.getByLabelText('Deadline (Europe/Oslo)'), { target: { value: '2026-12-01T15:30' } })
  await user.click(screen.getByLabelText('Accept late submissions'))
  await user.click(screen.getByRole('button', { name: 'Save deadline settings' }))
  expect(updateSubmissionPolicy).toHaveBeenCalledWith(7, { deadline: '2026-12-01T15:30', lateSubmissionsAccepted: true })
  expect(onSaved).toHaveBeenCalledOnce()
})

it('allows removing a deadline and keeps the form open after a rejected update', async () => {
  const user = userEvent.setup()
  const onSaved = vi.fn()
  updateSubmissionPolicy.mockRejectedValueOnce(new ApiError(403, 'Only course teachers may edit', null))
    .mockResolvedValueOnce(content)
  render(<EditSubmissionPolicy content={content} onSaved={onSaved} />)
  await user.click(screen.getByRole('button', { name: 'Edit deadline and late policy' }))
  fireEvent.change(screen.getByLabelText('Deadline (Europe/Oslo)'), { target: { value: '' } })
  await user.click(screen.getByRole('button', { name: 'Save deadline settings' }))
  expect(await screen.findByRole('alert')).toBeInTheDocument()
  expect(onSaved).not.toHaveBeenCalled()
  await user.click(screen.getByRole('button', { name: 'Save deadline settings' }))
  expect(updateSubmissionPolicy).toHaveBeenLastCalledWith(7, { deadline: null, lateSubmissionsAccepted: false })
  expect(onSaved).toHaveBeenCalledOnce()
})
