import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import CreateContentModal from './CreateContentModal'

const createStaticPage = vi.hoisted(() => vi.fn())
const createQuiz = vi.hoisted(() => vi.fn())
const createAssignment = vi.hoisted(() => vi.fn())
const createCodingExercise = vi.hoisted(() => vi.fn())
vi.mock('../services', () => ({
  contents: { createStaticPage, createQuiz, createAssignment, createCodingExercise },
}))

const staticPageResponse = {
  id: 1, moduleId: 1, type: 'StaticPage', title: 'Syllabus', position: 0,
  visibleFrom: null, visibleUntil: null, body: 'Welcome', instructions: null,
  repositoryUrl: null, deadline: null, lateSubmissionsAccepted: null, groupSubmission: null,
}

describe('CreateContentModal', () => {
  beforeEach(() => {
    createStaticPage.mockReset()
    createQuiz.mockReset()
    createAssignment.mockReset()
    createCodingExercise.mockReset()
  })

  it('creates a static page by default', async () => {
    const user = userEvent.setup()
    const onCreated = vi.fn()
    createStaticPage.mockResolvedValue(staticPageResponse)

    render(<CreateContentModal moduleId={1} open={true} onClose={vi.fn()} onCreated={onCreated} />)

    await user.type(screen.getByLabelText(/Title/), 'Syllabus')
    await user.type(screen.getByLabelText(/Body/), 'Welcome')
    await user.click(screen.getByRole('button', { name: 'Add content' }))

    expect(createStaticPage).toHaveBeenCalledWith(1,
      { title: 'Syllabus', position: 0, body: 'Welcome', visibleFrom: null, visibleUntil: null }, {})
    expect(onCreated).toHaveBeenCalledWith(staticPageResponse)
  })

  it('creates an assignment with group submission when selected', async () => {
    const user = userEvent.setup()
    createAssignment.mockResolvedValue({ ...staticPageResponse, type: 'Assignment' })

    render(<CreateContentModal moduleId={1} open={true} onClose={vi.fn()} onCreated={vi.fn()} />)

    await user.selectOptions(screen.getByLabelText('Type'), 'Assignment')
    await user.type(screen.getByLabelText(/Title/), 'Essay')
    await user.type(screen.getByLabelText(/Instructions/), 'Write 500 words')
    await user.click(screen.getByLabelText('Group submission'))
    await user.click(screen.getByRole('button', { name: 'Add content' }))

    expect(createAssignment).toHaveBeenCalledWith(1, {
      title: 'Essay', position: 0, instructions: 'Write 500 words', deadline: null,
      lateSubmissionsAccepted: false, groupSubmission: true,
    }, {})
  })

  it('creates a coding exercise with a repository URL', async () => {
    const user = userEvent.setup()
    createCodingExercise.mockResolvedValue({ ...staticPageResponse, type: 'CodingExercise' })

    render(<CreateContentModal moduleId={1} open={true} onClose={vi.fn()} onCreated={vi.fn()} />)

    await user.selectOptions(screen.getByLabelText('Type'), 'CodingExercise')
    await user.type(screen.getByLabelText(/Title/), 'Fix the bug')
    await user.type(screen.getByLabelText(/Repository URL/), 'https://example.com/repo')
    await user.click(screen.getByRole('button', { name: 'Add content' }))

    expect(createCodingExercise).toHaveBeenCalledWith(1, {
      title: 'Fix the bug', position: 0, repositoryUrl: 'https://example.com/repo', deadline: null,
      lateSubmissionsAccepted: false, groupSubmission: false,
    }, {})
  })

  it('creates a quiz with max attempts and hints', async () => {
    const user = userEvent.setup()
    createQuiz.mockResolvedValue({ ...staticPageResponse, type: 'Quiz' })

    render(<CreateContentModal moduleId={1} open={true} onClose={vi.fn()} onCreated={vi.fn()} />)

    await user.selectOptions(screen.getByLabelText('Type'), 'Quiz')
    await user.type(screen.getByLabelText(/Title/), 'Week 1 Quiz')
    await user.clear(screen.getByLabelText(/Max attempts/))
    await user.type(screen.getByLabelText(/Max attempts/), '3')
    await user.click(screen.getByLabelText('Show hints'))
    await user.click(screen.getByRole('button', { name: 'Add content' }))

    expect(createQuiz).toHaveBeenCalledWith(1,
      { title: 'Week 1 Quiz', position: 0, maxAttempts: 3, showHints: true, deadline: null }, {})
  })
})
