import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../services/apiClient'
import MyGroup from './MyGroup'

const getOwnGroup = vi.hoisted(() => vi.fn())
const getMembers = vi.hoisted(() => vi.fn())
vi.mock('../services', () => ({ groups: { getOwnGroup, getMembers } }))

describe('MyGroup', () => {
  beforeEach(() => {
    getOwnGroup.mockReset()
    getMembers.mockReset()
  })

  it('shows the group name and its members', async () => {
    getOwnGroup.mockResolvedValue({ id: 5, courseId: 1, name: 'Team A' })
    getMembers.mockResolvedValue([
      { id: 1, groupId: 5, email: 'me@example.com', joinedAt: '2026-01-01T00:00:00' },
      { id: 2, groupId: 5, email: 'friend@example.com', joinedAt: '2026-01-01T00:00:00' },
    ])
    render(<MyGroup courseId={1} />)

    expect(await screen.findByRole('heading', { name: 'Team A' })).toBeInTheDocument()
    expect(screen.getByText('friend@example.com')).toBeInTheDocument()
  })

  it('renders nothing when the student has no group', async () => {
    getOwnGroup.mockRejectedValue(new ApiError(404, 'not found', null))
    const { container } = render(<MyGroup courseId={1} />)

    await vi.waitFor(() => expect(getOwnGroup).toHaveBeenCalled())
    expect(container).toBeEmptyDOMElement()
  })

  it('tells the student when the group could not be loaded', async () => {
    getOwnGroup.mockRejectedValue(new ApiError(500, 'boom', null))
    render(<MyGroup courseId={1} />)

    expect(await screen.findByText('Your group could not be loaded.')).toBeInTheDocument()
  })
})
