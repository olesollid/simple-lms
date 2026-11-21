import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../services/apiClient'
import PageCompletion from './PageCompletion'

const getOwnCompletions = vi.hoisted(() => vi.fn())
const markCompleted = vi.hoisted(() => vi.fn())
const getModules = vi.hoisted(() => vi.fn())
vi.mock('../services', () => ({ contents: { getOwnCompletions, markCompleted }, modules: { getModules } }))

const week1 = { id: 4, courseId: 1, title: 'Week 1', dependencyIds: [], locked: false }
const week2 = { id: 5, courseId: 1, title: 'Week 2', dependencyIds: [4], locked: true }

const completion = (contentId: number) => ({
  id: 1, contentId, userEmail: 'student@example.com', completedAt: '2026-11-21T10:00:00',
})

describe('PageCompletion', () => {
  beforeEach(() => {
    getOwnCompletions.mockReset().mockResolvedValue([])
    markCompleted.mockReset().mockResolvedValue(undefined)
    getModules.mockReset().mockResolvedValue([week1, week2])
  })

  it('marks the page as complete', async () => {
    const user = userEvent.setup()
    render(<PageCompletion courseId={1} moduleId={4} contentId={7} />)

    await user.click(await screen.findByRole('button', { name: 'Mark as complete' }))

    expect(markCompleted).toHaveBeenCalledWith(7, {})
    expect(await screen.findByText('✓ Completed')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Mark as complete' })).not.toBeInTheDocument()
  })

  it('shows a page the student already completed as completed', async () => {
    getOwnCompletions.mockResolvedValue([completion(3), completion(7)])
    render(<PageCompletion courseId={1} moduleId={4} contentId={7} />)

    expect(await screen.findByText('✓ Completed')).toBeInTheDocument()
    expect(getOwnCompletions).toHaveBeenCalledWith(1, expect.anything())
  })

  it('keeps the button and shows the reason when marking fails', async () => {
    markCompleted.mockRejectedValue(new ApiError(404, 'Content not found: 7', null))
    const user = userEvent.setup()
    render(<PageCompletion courseId={1} moduleId={4} contentId={7} />)

    await user.click(await screen.findByRole('button', { name: 'Mark as complete' }))

    expect(await screen.findByText('Content not found: 7')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Mark as complete' })).toBeInTheDocument()
  })

  it('shows nothing when the page is in a module that no other module requires', async () => {
    getModules.mockResolvedValue([week1, { ...week2, dependencyIds: [] }])
    render(<PageCompletion courseId={1} moduleId={4} contentId={7} />)

    await vi.waitFor(() => expect(getModules).toHaveBeenCalledWith(1, expect.anything()))
    expect(screen.queryByRole('button', { name: 'Mark as complete' })).not.toBeInTheDocument()
    expect(screen.queryByText(/counts toward unlocking/)).not.toBeInTheDocument()
    expect(screen.queryByText('✓ Completed')).not.toBeInTheDocument()
  })

  it('shows the button for a page in a module that unlocks another one', async () => {
    render(<PageCompletion courseId={1} moduleId={4} contentId={7} />)

    expect(await screen.findByRole('button', { name: 'Mark as complete' })).toBeInTheDocument()
    expect(screen.getByText(/counts toward unlocking/)).toBeInTheDocument()
  })
})
