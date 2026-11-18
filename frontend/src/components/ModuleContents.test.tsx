import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../services/apiClient'
import ModuleContents from './ModuleContents'

const getContentsByModule = vi.hoisted(() => vi.fn())
vi.mock('../services', () => ({ contents: { getContentsByModule } }))

function renderWithRouter() {
  return render(
    <MemoryRouter>
      <ModuleContents moduleId={1} />
    </MemoryRouter>,
  )
}

describe('ModuleContents', () => {
  beforeEach(() => {
    getContentsByModule.mockReset()
  })

  it('shows a message when the module has no content', async () => {
    getContentsByModule.mockResolvedValue([])
    renderWithRouter()

    expect(await screen.findByText('No content yet.')).toBeInTheDocument()
  })

  it('lists content with its type', async () => {
    getContentsByModule.mockResolvedValue([
      { id: 1, moduleId: 1, type: 'StaticPage', title: 'Syllabus', position: 0,
        visibleFrom: null, visibleUntil: null, body: 'Welcome', instructions: null,
        repositoryUrl: null, deadline: null, lateSubmissionsAccepted: null, groupSubmission: null },
      { id: 2, moduleId: 1, type: 'Assignment', title: 'Essay', position: 1,
        visibleFrom: null, visibleUntil: null, body: null, instructions: 'Write 500 words',
        repositoryUrl: null, deadline: null, lateSubmissionsAccepted: false, groupSubmission: false },
    ])
    renderWithRouter()

    expect(await screen.findByText('Syllabus')).toBeInTheDocument()
    expect(screen.getByText('Page')).toBeInTheDocument()
    expect(screen.getByText('Essay')).toBeInTheDocument()
    expect(screen.getByText('Assignment')).toBeInTheDocument()
  })

  it('shows an error message on failure', async () => {
    getContentsByModule.mockRejectedValue(new ApiError(403, 'Forbidden', null))
    renderWithRouter()

    expect(await screen.findByRole('alert')).toHaveTextContent(/permission/i)
  })
})
