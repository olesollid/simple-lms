import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../services/apiClient'
import ContentDetailPage from './ContentDetailPage'

const getContent = vi.hoisted(() => vi.fn())
const getModule = vi.hoisted(() => vi.fn())
const getCurrentUser = vi.hoisted(() => vi.fn())
const getEnrollment = vi.hoisted(() => vi.fn())
const getOwnSubmission = vi.hoisted(() => vi.fn())
const submit = vi.hoisted(() => vi.fn())
const getOwnGroup = vi.hoisted(() => vi.fn())

vi.mock('../services', () => ({
  contents: { getContent },
  modules: { getModule },
  users: { getCurrentUser },
  enrollments: { getEnrollment },
  submissions: { getOwnSubmission, submit },
  groups: { getOwnGroup },
}))

const staticPage = {
  id: 1, moduleId: 1, type: 'StaticPage', title: 'Syllabus', position: 0,
  visibleFrom: null, visibleUntil: null, body: 'Welcome to the course!', instructions: null,
  repositoryUrl: null, deadline: null, lateSubmissionsAccepted: null, groupSubmission: null,
}

const assignment = {
  id: 2, moduleId: 1, type: 'Assignment', title: 'Essay', position: 1,
  visibleFrom: null, visibleUntil: null, body: null, instructions: 'Write 500 words',
  repositoryUrl: null, deadline: null, lateSubmissionsAccepted: false, groupSubmission: false,
}

const groupAssignment = { ...assignment, id: 3, title: 'Group Essay', groupSubmission: true }

const module1 = { id: 1, courseId: 1, title: 'Week 1', dependencyIds: [] }

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/contents/:contentId" element={<ContentDetailPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('ContentDetailPage', () => {
  beforeEach(() => {
    getContent.mockReset()
    getModule.mockReset()
    getCurrentUser.mockReset()
    getEnrollment.mockReset()
    getOwnSubmission.mockReset()
    submit.mockReset()
    getOwnGroup.mockReset()
    getModule.mockResolvedValue(module1)
    getCurrentUser.mockResolvedValue({ email: 'student@example.com', name: 'Student' })
  })

  it('shows a static page body for a student', async () => {
    getContent.mockResolvedValue(staticPage)
    getEnrollment.mockResolvedValue({ role: 'STUDENT' })
    renderAt('/contents/1')

    expect(await screen.findByRole('heading', { level: 1, name: 'Syllabus' })).toBeInTheDocument()
    expect(screen.getByText('Welcome to the course!')).toBeInTheDocument()
  })

  it('shows the submission form for an assignment when the student has not submitted yet', async () => {
    getContent.mockResolvedValue(assignment)
    getEnrollment.mockResolvedValue({ role: 'STUDENT' })
    getOwnSubmission.mockRejectedValue(new ApiError(404, 'not found', null))
    renderAt('/contents/2')

    expect(await screen.findByText('Write 500 words')).toBeInTheDocument()
    expect(screen.getByLabelText('Your submission')).toBeInTheDocument()
  })

  it('does not show a submission form for a teacher', async () => {
    getContent.mockResolvedValue(assignment)
    getEnrollment.mockResolvedValue({ role: 'TEACHER' })
    renderAt('/contents/2')

    expect(await screen.findByText('Write 500 words')).toBeInTheDocument()
    expect(screen.queryByLabelText('Your submission')).not.toBeInTheDocument()
    expect(getOwnSubmission).not.toHaveBeenCalled()
  })

  it('shows an error for an invalid content id', async () => {
    renderAt('/contents/not-a-number')

    expect(await screen.findByRole('alert')).toHaveTextContent('could not be found')
    expect(getContent).not.toHaveBeenCalled()
  })

  it('asks a groupless student to join a group instead of showing a submission form', async () => {
    getContent.mockResolvedValue(groupAssignment)
    getEnrollment.mockResolvedValue({ role: 'STUDENT' })
    getOwnGroup.mockRejectedValue(new ApiError(404, 'not found', null))
    renderAt('/contents/3')

    expect(await screen.findByText(/ask your teacher to add you to a group/i)).toBeInTheDocument()
    expect(screen.queryByLabelText('Your submission')).not.toBeInTheDocument()
    expect(getOwnSubmission).not.toHaveBeenCalled()
  })

  it('shows the submission form for a group assignment once the student has a group', async () => {
    getContent.mockResolvedValue(groupAssignment)
    getEnrollment.mockResolvedValue({ role: 'STUDENT' })
    getOwnGroup.mockResolvedValue({ id: 1, courseId: 1, name: 'Team A' })
    getOwnSubmission.mockRejectedValue(new ApiError(404, 'not found', null))
    renderAt('/contents/3')

    expect(await screen.findByLabelText('Your submission')).toBeInTheDocument()
  })
})
