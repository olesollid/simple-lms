import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../services/apiClient'
import ManageGroupsModal from './ManageGroupsModal'

const getEnrollments = vi.hoisted(() => vi.fn())
const getGroupsByCourse = vi.hoisted(() => vi.fn())
const getMembers = vi.hoisted(() => vi.fn())
const createGroup = vi.hoisted(() => vi.fn())
const addMember = vi.hoisted(() => vi.fn())
const removeMember = vi.hoisted(() => vi.fn())
const deleteGroup = vi.hoisted(() => vi.fn())
vi.mock('../services', () => ({
  enrollments: { getEnrollments },
  groups: { getGroupsByCourse, getMembers, createGroup, addMember, removeMember, deleteGroup },
}))

const student = (id: number, name: string, email: string) => ({
  id, courseId: 1, courseTitle: 'DAT250', email, name, role: 'STUDENT', enrolledAt: '2026-01-01T00:00:00',
})
const bob = student(1, 'Bob Studentson', 'bob@example.com')
const carol = student(2, 'Carol Learner', 'carol@example.com')
const teamA = { id: 10, courseId: 1, name: 'Team A' }
const membership = (email: string) => ({ id: 1, groupId: 10, email, joinedAt: '2026-01-01T00:00:00' })

describe('ManageGroupsModal', () => {
  beforeEach(() => {
    for (const mock of [getEnrollments, getGroupsByCourse, getMembers, createGroup, addMember, removeMember, deleteGroup]) {
      mock.mockReset()
    }
    getEnrollments.mockResolvedValue([bob, carol])
    getGroupsByCourse.mockResolvedValue([])
    getMembers.mockResolvedValue([])
  })

  it('does not load anything while closed', () => {
    render(<ManageGroupsModal courseId={1} open={false} onClose={vi.fn()} />)

    expect(getGroupsByCourse).not.toHaveBeenCalled()
  })

  it('shows an empty state when the course has no groups', async () => {
    render(<ManageGroupsModal courseId={1} open={true} onClose={vi.fn()} />)

    expect(await screen.findByText(/No groups yet/)).toBeInTheDocument()
    expect(getEnrollments).toHaveBeenCalledWith(1, 'STUDENT', expect.anything())
  })

  it('creates a group and reloads the list', async () => {
    const user = userEvent.setup()
    createGroup.mockResolvedValue(teamA)
    getGroupsByCourse.mockResolvedValueOnce([]).mockResolvedValueOnce([teamA])
    render(<ManageGroupsModal courseId={1} open={true} onClose={vi.fn()} />)

    await screen.findByText(/No groups yet/)
    await user.type(screen.getByLabelText(/Group name/), 'Team A')
    await user.click(screen.getByRole('button', { name: 'Create group' }))

    expect(createGroup).toHaveBeenCalledWith(1, 'Team A', {})
    expect(await screen.findByRole('heading', { name: 'Team A' })).toBeInTheDocument()
  })

  it('adds an unassigned student to a group', async () => {
    const user = userEvent.setup()
    getGroupsByCourse.mockResolvedValue([teamA])
    getMembers.mockResolvedValueOnce([]).mockResolvedValueOnce([membership('bob@example.com')])
    addMember.mockResolvedValue(membership('bob@example.com'))
    render(<ManageGroupsModal courseId={1} open={true} onClose={vi.fn()} />)

    const group = await screen.findByRole('region', { name: 'Group Team A' })
    await user.selectOptions(within(group).getByLabelText(/Add student to Team A/), 'bob@example.com')
    await user.click(within(group).getByRole('button', { name: 'Add' }))

    expect(addMember).toHaveBeenCalledWith(10, 'bob@example.com', {})
    expect(await within(group).findByText('Bob Studentson')).toBeInTheDocument()
  })

  it('only offers students who are not in a group yet', async () => {
    getGroupsByCourse.mockResolvedValue([teamA])
    getMembers.mockResolvedValue([membership('bob@example.com')])
    render(<ManageGroupsModal courseId={1} open={true} onClose={vi.fn()} />)

    const select = await screen.findByLabelText(/Add student to Team A/)
    expect(within(select).queryByRole('option', { name: /Bob/ })).not.toBeInTheDocument()
    expect(within(select).getByRole('option', { name: /Carol/ })).toBeInTheDocument()
  })

  it('removes a member from a group', async () => {
    const user = userEvent.setup()
    getGroupsByCourse.mockResolvedValue([teamA])
    getMembers.mockResolvedValueOnce([membership('bob@example.com')]).mockResolvedValueOnce([])
    removeMember.mockResolvedValue(undefined)
    render(<ManageGroupsModal courseId={1} open={true} onClose={vi.fn()} />)

    await screen.findByText('bob@example.com')
    await user.click(screen.getByRole('button', { name: 'Remove' }))

    expect(removeMember).toHaveBeenCalledWith(10, 'bob@example.com', {})
    expect(await screen.findByText('No members yet.')).toBeInTheDocument()
  })

  it('shows the server error when adding a student fails', async () => {
    const user = userEvent.setup()
    getGroupsByCourse.mockResolvedValue([teamA])
    addMember.mockRejectedValue(new ApiError(409, 'bob@example.com already belongs to a group', null))
    render(<ManageGroupsModal courseId={1} open={true} onClose={vi.fn()} />)

    const group = await screen.findByRole('region', { name: 'Group Team A' })
    await user.selectOptions(within(group).getByLabelText(/Add student to Team A/), 'bob@example.com')
    await user.click(within(group).getByRole('button', { name: 'Add' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('already belongs to a group')
  })

  it('shows an error and a retry button when loading fails', async () => {
    getGroupsByCourse.mockRejectedValue(new ApiError(500, 'Server exploded', null))
    render(<ManageGroupsModal courseId={1} open={true} onClose={vi.fn()} />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Server exploded')
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument()
  })

  it('deletes a group after confirmation and reloads the list', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    getGroupsByCourse.mockResolvedValueOnce([teamA]).mockResolvedValueOnce([])
    deleteGroup.mockResolvedValue(undefined)
    render(<ManageGroupsModal courseId={1} open={true} onClose={vi.fn()} />)

    await screen.findByRole('heading', { name: 'Team A' })
    await user.click(screen.getByRole('button', { name: 'Delete group' }))

    expect(deleteGroup).toHaveBeenCalledWith(10, {})
    expect(await screen.findByText(/No groups yet/)).toBeInTheDocument()
  })

  it('does not delete a group when the confirmation is declined', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    getGroupsByCourse.mockResolvedValue([teamA])
    render(<ManageGroupsModal courseId={1} open={true} onClose={vi.fn()} />)

    await screen.findByRole('heading', { name: 'Team A' })
    await user.click(screen.getByRole('button', { name: 'Delete group' }))

    expect(deleteGroup).not.toHaveBeenCalled()
  })

  it('explains why a group with submissions cannot be deleted', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    getGroupsByCourse.mockResolvedValue([teamA])
    deleteGroup.mockRejectedValue(new ApiError(409, 'Group 10 has submissions', null))
    render(<ManageGroupsModal courseId={1} open={true} onClose={vi.fn()} />)

    await screen.findByRole('heading', { name: 'Team A' })
    await user.click(screen.getByRole('button', { name: 'Delete group' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('has submissions and cannot be deleted')
  })
})
