import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../services/apiClient'
import ContentDetailPage from './ContentDetailPage'

const getContent = vi.hoisted(() => vi.fn())
const getOwnCompletions = vi.hoisted(() => vi.fn())
const markCompleted = vi.hoisted(() => vi.fn())
const deleteContent = vi.hoisted(() => vi.fn())
const getModule = vi.hoisted(() => vi.fn())
const getModules = vi.hoisted(() => vi.fn())
const getCurrentUser = vi.hoisted(() => vi.fn())
const getEnrollment = vi.hoisted(() => vi.fn())
const getOwnSubmission = vi.hoisted(() => vi.fn())
const submit = vi.hoisted(() => vi.fn())
const getOwnGroup = vi.hoisted(() => vi.fn())
const getSubmissionsByContent = vi.hoisted(() => vi.fn())
const getQuestions = vi.hoisted(() => vi.fn())
const getQuestionsWithAnswers = vi.hoisted(() => vi.fn())
const getOwnAttempts = vi.hoisted(() => vi.fn())

vi.mock('../services', () => ({
  contents: { getContent, deleteContent, getOwnCompletions, markCompleted },
  modules: { getModule, getModules },
  users: { getCurrentUser },
  enrollments: { getEnrollment },
  submissions: { getOwnSubmission, submit, getSubmissionsByContent, gradesPublished: () => Promise.resolve(false) },
  groups: { getOwnGroup },
  quizzes: { getQuestions, getQuestionsWithAnswers, getOwnAttempts },
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

const quiz = { ...staticPage, id: 4, type: 'Quiz', title: 'Week 1 quiz', body: null }

const module1 = { id: 1, courseId: 1, title: 'Week 1', dependencyIds: [], locked: false }

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/courses/:courseId/modules/:moduleId/contents/:contentId"
          element={<ContentDetailPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('ContentDetailPage', () => {
  beforeEach(() => {
    getOwnCompletions.mockReset().mockResolvedValue([])
    markCompleted.mockReset().mockResolvedValue(undefined)
    getContent.mockReset()
    deleteContent.mockReset()
    getModule.mockReset()
    getCurrentUser.mockReset()
    getEnrollment.mockReset()
    getOwnSubmission.mockReset()
    submit.mockReset()
    getOwnGroup.mockReset()
    getSubmissionsByContent.mockReset()
    getSubmissionsByContent.mockResolvedValue([])
    getModule.mockResolvedValue(module1)
    getModules.mockReset().mockResolvedValue([module1, { ...module1, id: 2, title: 'Week 2', dependencyIds: [1] }])
    getCurrentUser.mockResolvedValue({ email: 'student@example.com', name: 'Student' })
  })

  it('shows a static page body for a student', async () => {
    getContent.mockResolvedValue(staticPage)
    getEnrollment.mockResolvedValue({ role: 'STUDENT' })
    renderAt('/courses/1/modules/1/contents/1')

    expect(await screen.findByRole('heading', { level: 1, name: 'Syllabus' })).toBeInTheDocument()
    expect(screen.getByText('Welcome to the course!')).toBeInTheDocument()
  })

  it('renders markdown and images in a static page body', async () => {
    getContent.mockResolvedValue({
      ...staticPage,
      body: '## Reading\n\nSee **this** diagram:\n\n![Class diagram](https://example.com/uml.png)',
    })
    getEnrollment.mockResolvedValue({ role: 'STUDENT' })
    renderAt('/courses/1/modules/1/contents/1')

    expect(await screen.findByRole('heading', { level: 2, name: 'Reading' })).toBeInTheDocument()
    expect(screen.getByText('this').tagName).toBe('STRONG')
    expect(screen.getByRole('img', { name: 'Class diagram' })).toHaveAttribute('src', 'https://example.com/uml.png')
  })

  it('lets a teacher open the edit form and see when students can see the item', async () => {
    getContent.mockResolvedValue({ ...staticPage, visibleFrom: '2026-12-01T08:00:00', visibleToStudents: false })
    getEnrollment.mockResolvedValue({ role: 'TEACHER' })
    const user = userEvent.setup()
    renderAt('/courses/1/modules/1/contents/1')

    expect(await screen.findByText(
      'Visible to students from 2026-12-01 08:00 (Europe/Oslo). Hidden from students right now.')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Edit content' }))
    expect(screen.getByRole('form', { name: 'Edit content' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(screen.queryByRole('form', { name: 'Edit content' })).not.toBeInTheDocument()
  })

  it('lets a student mark a page as complete, but not a teacher', async () => {
    getContent.mockResolvedValue(staticPage)
    getEnrollment.mockResolvedValue({ role: 'STUDENT' })
    const { unmount } = renderAt('/courses/1/modules/1/contents/1')

    expect(await screen.findByRole('button', { name: 'Mark as complete' })).toBeInTheDocument()
    unmount()

    getEnrollment.mockResolvedValue({ role: 'TEACHER' })
    renderAt('/courses/1/modules/1/contents/1')
    expect(await screen.findByText('Welcome to the course!')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Mark as complete' })).not.toBeInTheDocument()
  })

  it('shows the quiz editor for a teacher', async () => {
    getContent.mockResolvedValue(quiz)
    getEnrollment.mockResolvedValue({ role: 'TEACHER' })
    getQuestionsWithAnswers.mockResolvedValue([])
    renderAt('/courses/1/modules/1/contents/4')

    expect(await screen.findByRole('heading', { name: 'Add question' })).toBeInTheDocument()
    expect(getQuestions).not.toHaveBeenCalled()
  })

  it('shows the quiz deadline in Oslo time', async () => {
    getContent.mockResolvedValue({ ...quiz, deadline: '2026-12-01T12:00:00' })
    getEnrollment.mockResolvedValue({ role: 'STUDENT' })
    getOwnAttempts.mockResolvedValue({ attemptsUsed: 0, maxAttempts: 1, deadline: '2026-12-01T12:00:00', closed: false, best: null, latest: null })
    getQuestions.mockResolvedValue([{ id: 1, text: 'What is 2+2?', position: 0, options: [{ id: 10, text: '4' }] }])
    renderAt('/courses/1/modules/1/contents/4')

    expect(await screen.findByText('Deadline: 2026-12-01 12:00:00 (Europe/Oslo). The quiz closes after it.')).toBeInTheDocument()
  })

  it('lets a student take a quiz', async () => {
    getContent.mockResolvedValue(quiz)
    getEnrollment.mockResolvedValue({ role: 'STUDENT' })
    getOwnAttempts.mockResolvedValue({ attemptsUsed: 0, maxAttempts: 1, best: null, latest: null })
    getQuestions.mockResolvedValue([{ id: 1, text: 'What is 2+2?', position: 0, options: [{ id: 10, text: '4' }] }])
    renderAt('/courses/1/modules/1/contents/4')

    expect(await screen.findByText('What is 2+2?')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Submit quiz' })).toBeInTheDocument()
    expect(getQuestionsWithAnswers).not.toHaveBeenCalled()
  })

  it('shows the submission form for an assignment when the student has not submitted yet', async () => {
    getContent.mockResolvedValue(assignment)
    getEnrollment.mockResolvedValue({ role: 'STUDENT' })
    getOwnSubmission.mockRejectedValue(new ApiError(404, 'not found', null))
    renderAt('/courses/1/modules/1/contents/2')

    expect(await screen.findByText('Write 500 words')).toBeInTheDocument()
    expect(screen.getByLabelText(/Your submission/)).toBeInTheDocument()
  })

  it('shows the submissions review instead of a submission form for a teacher', async () => {
    getContent.mockResolvedValue(assignment)
    getEnrollment.mockResolvedValue({ role: 'TEACHER' })
    getSubmissionsByContent.mockResolvedValue([])
    renderAt('/courses/1/modules/1/contents/2')

    expect(await screen.findByText('Write 500 words')).toBeInTheDocument()
    expect(screen.queryByLabelText(/Your submission/)).not.toBeInTheDocument()
    expect(getOwnSubmission).not.toHaveBeenCalled()
    expect(await screen.findByText('No submissions yet.')).toBeInTheDocument()
  })

  it('shows an error for an invalid content id', async () => {
    renderAt('/courses/1/modules/1/contents/not-a-number')

    expect(await screen.findByRole('alert')).toHaveTextContent('could not be found')
    expect(getContent).not.toHaveBeenCalled()
  })

  it('asks a groupless student to join a group instead of showing a submission form', async () => {
    getContent.mockResolvedValue(groupAssignment)
    getEnrollment.mockResolvedValue({ role: 'STUDENT' })
    getOwnGroup.mockRejectedValue(new ApiError(404, 'not found', null))
    renderAt('/courses/1/modules/1/contents/3')

    expect(await screen.findByText(/ask your teacher to add you to a group/i)).toBeInTheDocument()
    expect(screen.queryByLabelText(/Your submission/)).not.toBeInTheDocument()
    expect(getOwnSubmission).not.toHaveBeenCalled()
  })

  it('shows the submission form for a group assignment once the student has a group', async () => {
    getContent.mockResolvedValue(groupAssignment)
    getEnrollment.mockResolvedValue({ role: 'STUDENT' })
    getOwnGroup.mockResolvedValue({ id: 1, courseId: 1, name: 'Team A' })
    getOwnSubmission.mockRejectedValue(new ApiError(404, 'not found', null))
    renderAt('/courses/1/modules/1/contents/3')

    expect(await screen.findByLabelText(/Your submission/)).toBeInTheDocument()
    expect(screen.getByText('Team A')).toBeInTheDocument()
  })

  it('does not show a "Delete content" button for a student', async () => {
    getContent.mockResolvedValue(staticPage)
    getEnrollment.mockResolvedValue({ role: 'STUDENT' })
    renderAt('/courses/1/modules/1/contents/1')

    expect(await screen.findByRole('heading', { level: 1, name: 'Syllabus' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Delete content' })).not.toBeInTheDocument()
  })

  describe('as a teacher deleting content', () => {
    const confirmSpy = vi.spyOn(window, 'confirm')

    afterEach(() => confirmSpy.mockReset())

    it('deletes the content after confirming', async () => {
      const user = userEvent.setup()
      confirmSpy.mockReturnValue(true)
      getContent.mockResolvedValue(staticPage)
      getEnrollment.mockResolvedValue({ role: 'TEACHER' })
      deleteContent.mockResolvedValue(undefined)
      renderAt('/courses/1/modules/1/contents/1')

      await user.click(await screen.findByRole('button', { name: 'Delete content' }))

      expect(deleteContent).toHaveBeenCalledWith(1, {})
    })

    it('shows an error when deletion fails', async () => {
      const user = userEvent.setup()
      confirmSpy.mockReturnValue(true)
      getContent.mockResolvedValue(staticPage)
      getEnrollment.mockResolvedValue({ role: 'TEACHER' })
      deleteContent.mockRejectedValue(new ApiError(403, 'Forbidden', null))
      renderAt('/courses/1/modules/1/contents/1')

      await user.click(await screen.findByRole('button', { name: 'Delete content' }))

      expect(await screen.findByRole('alert')).toHaveTextContent(/permission/i)
    })
  })
})
