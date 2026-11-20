import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../services/apiClient'
import ModuleContents from './ModuleContents'

const getContentsByModule = vi.hoisted(() => vi.fn())
const deleteContent = vi.hoisted(() => vi.fn())
vi.mock('../services', () => ({ contents: { getContentsByModule, deleteContent } }))

function renderWithRouter(isTeacher = false) {
  return render(
    <MemoryRouter>
      <ModuleContents courseId={1} moduleId={1} isTeacher={isTeacher} />
    </MemoryRouter>,
  )
}

const page = { id: 1, moduleId: 1, type: 'StaticPage', title: 'Syllabus', position: 0,
  visibleFrom: null, visibleUntil: null, body: 'Welcome', instructions: null,
  repositoryUrl: null, deadline: null, lateSubmissionsAccepted: null, groupSubmission: null }
const assignment = { id: 2, moduleId: 1, type: 'Assignment', title: 'Essay', position: 1,
  visibleFrom: null, visibleUntil: null, body: null, instructions: 'Write 500 words',
  repositoryUrl: null, deadline: null, lateSubmissionsAccepted: false, groupSubmission: false }

describe('ModuleContents', () => {
  beforeEach(() => {
    getContentsByModule.mockReset()
    deleteContent.mockReset()
  })

  it('shows a message when the module has no content', async () => {
    getContentsByModule.mockResolvedValue([])
    renderWithRouter()

    expect(await screen.findByText('No content yet.')).toBeInTheDocument()
  })

  it('lists content with its type', async () => {
    getContentsByModule.mockResolvedValue([page, assignment])
    renderWithRouter()

    expect(await screen.findByText('Syllabus')).toBeInTheDocument()
    expect(screen.getByText('Page')).toBeInTheDocument()
    expect(screen.getByText('Essay')).toBeInTheDocument()
    expect(screen.getByText('Assignment')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Syllabus/ }))
      .toHaveAttribute('href', '/courses/1/modules/1/contents/1')
  })

  it('shows an error message on failure', async () => {
    getContentsByModule.mockRejectedValue(new ApiError(403, 'Forbidden', null))
    renderWithRouter()

    expect(await screen.findByRole('alert')).toHaveTextContent(/permission/i)
  })

  it('does not show delete buttons for a student', async () => {
    getContentsByModule.mockResolvedValue([page])
    renderWithRouter(false)

    expect(await screen.findByText('Syllabus')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument()
  })

  describe('as a teacher', () => {
    const confirmSpy = vi.spyOn(window, 'confirm')

    afterEach(() => confirmSpy.mockReset())

    it('deletes content after confirming', async () => {
      const user = userEvent.setup()
      confirmSpy.mockReturnValue(true)
      getContentsByModule.mockResolvedValueOnce([page]).mockResolvedValueOnce([])
      deleteContent.mockResolvedValue(undefined)
      renderWithRouter(true)

      await user.click(await screen.findByRole('button', { name: 'Delete' }))

      expect(deleteContent).toHaveBeenCalledWith(1, {})
      expect(await screen.findByText('No content yet.')).toBeInTheDocument()
    })

    it('does not delete when the confirmation is declined', async () => {
      const user = userEvent.setup()
      confirmSpy.mockReturnValue(false)
      getContentsByModule.mockResolvedValue([page])
      renderWithRouter(true)

      await user.click(await screen.findByRole('button', { name: 'Delete' }))

      expect(deleteContent).not.toHaveBeenCalled()
    })

    it('shows an error when deletion fails', async () => {
      const user = userEvent.setup()
      confirmSpy.mockReturnValue(true)
      getContentsByModule.mockResolvedValue([page])
      deleteContent.mockRejectedValue(new ApiError(403, 'Forbidden', null))
      renderWithRouter(true)

      await user.click(await screen.findByRole('button', { name: 'Delete' }))

      expect(await screen.findByRole('alert')).toHaveTextContent(/permission/i)
    })
  })
})
