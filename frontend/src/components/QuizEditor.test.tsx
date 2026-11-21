import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../services/apiClient'
import QuizEditor from './QuizEditor'

const getQuestionsWithAnswers = vi.hoisted(() => vi.fn())
const addQuestion = vi.hoisted(() => vi.fn())
const updateQuestion = vi.hoisted(() => vi.fn())
const deleteQuestion = vi.hoisted(() => vi.fn())
const publish = vi.hoisted(() => vi.fn())
const getResults = vi.hoisted(() => vi.fn())
const setResultVisibility = vi.hoisted(() => vi.fn())
const publishGrades = vi.hoisted(() => vi.fn())
vi.mock('../services', () => ({
  quizzes: { getQuestionsWithAnswers, addQuestion, updateQuestion, deleteQuestion, publish, getResults, setResultVisibility },
  submissions: { publishGrades },
}))

const draftQuiz = {
  id: 5, moduleId: 1, type: 'Quiz' as const, title: 'Week 1 quiz', position: 0,
  visibleFrom: null, visibleUntil: null, body: null, instructions: null, repositoryUrl: null,
  deadline: null, lateSubmissionsAccepted: null, groupSubmission: null, maxAttempts: 3,
  published: false,
}
const publishedQuiz = { ...draftQuiz, published: true }

const existing = {
  id: 1, text: 'What is 2+2?', position: 0, hint: 'Count on your fingers',
  options: [{ id: 10, text: '3', correct: false }, { id: 11, text: '4', correct: true }],
}

async function fillNewQuestion(user: ReturnType<typeof userEvent.setup>, text: string) {
  const form = within(await screen.findByRole('form', { name: 'Add question' }))
  await user.type(form.getByLabelText(/Question/), text)
  await user.type(form.getByLabelText('Option 1'), 'Bergen')
  await user.type(form.getByLabelText('Option 2'), 'Oslo')
  return form
}

describe('QuizEditor', () => {
  beforeEach(() => {
    getQuestionsWithAnswers.mockReset().mockResolvedValue([])
    addQuestion.mockReset()
    updateQuestion.mockReset()
    deleteQuestion.mockReset().mockResolvedValue(undefined)
    publish.mockReset().mockResolvedValue({ ...publishedQuiz })
    getResults.mockReset().mockResolvedValue([])
    setResultVisibility.mockReset().mockResolvedValue({})
    publishGrades.mockReset().mockResolvedValue(true)
    vi.spyOn(window, 'confirm').mockReturnValue(true)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('shows questions with the correct answer and hint for a draft quiz', async () => {
    getQuestionsWithAnswers.mockResolvedValue([existing])
    render(<QuizEditor quiz={draftQuiz} />)

    expect(await screen.findByText('What is 2+2?')).toBeInTheDocument()
    expect(screen.getByText('4').closest('li')).toHaveClass('quiz-option-correct')
    expect(screen.getByText('3').closest('li')).not.toHaveClass('quiz-option-correct')
    expect(screen.getByText('Count on your fingers')).toBeInTheDocument()
    expect(screen.getByText(/Students cannot see this quiz yet/)).toBeInTheDocument()
    expect(screen.getByText(/3 attempts per student/)).toBeInTheDocument()
    expect(screen.getByText(/given only when the student selects every correct option/)).toBeInTheDocument()
  })

  it('adds a question with a hint and clears the form', async () => {
    addQuestion.mockResolvedValue({ ...existing, id: 2, text: 'Capital of Norway?', hint: null })
    const user = userEvent.setup()
    render(<QuizEditor quiz={draftQuiz} />)

    const form = await fillNewQuestion(user, 'Capital of Norway?')
    await user.click(form.getByLabelText('Option 2 is correct'))
    await user.type(form.getByLabelText('Hint (optional)'), 'It starts with O')
    await user.click(form.getByRole('button', { name: 'Add option' }))
    await user.click(form.getByRole('button', { name: 'Save question' }))

    expect(addQuestion).toHaveBeenCalledWith(5, {
      text: 'Capital of Norway?',
      hint: 'It starts with O',
      options: [{ text: 'Bergen', correct: false }, { text: 'Oslo', correct: true }],
    }, {})
    expect(await screen.findByText('Capital of Norway?')).toBeInTheDocument()
    expect(form.getByLabelText(/Question/)).toHaveValue('')
    expect(form.getByLabelText('Hint (optional)')).toHaveValue('')
  })

  it('sends no hint when the hint field is left empty', async () => {
    addQuestion.mockResolvedValue({ ...existing, id: 2 })
    const user = userEvent.setup()
    render(<QuizEditor quiz={draftQuiz} />)

    const form = await fillNewQuestion(user, 'Q')
    await user.click(form.getByLabelText('Option 1 is correct'))
    await user.click(form.getByRole('button', { name: 'Save question' }))

    expect(addQuestion).toHaveBeenCalledWith(5, expect.objectContaining({ hint: null }), {})
  })

  it('requires at least one correct option and two filled-in options', async () => {
    const user = userEvent.setup()
    render(<QuizEditor quiz={draftQuiz} />)

    const form = await fillNewQuestion(user, 'Pick one')
    await user.click(form.getByRole('button', { name: 'Save question' }))
    expect(form.getByText('Mark at least one option as correct.')).toBeInTheDocument()

    await user.clear(form.getByLabelText('Option 2'))
    await user.click(form.getByLabelText('Option 1 is correct'))
    await user.click(form.getByRole('button', { name: 'Save question' }))
    expect(form.getByText('Add at least two answer options.')).toBeInTheDocument()
    expect(addQuestion).not.toHaveBeenCalled()
  })

  it('edits an existing question, starting from its current values', async () => {
    getQuestionsWithAnswers.mockResolvedValue([existing])
    updateQuestion.mockResolvedValue({ ...existing, text: 'What is 2+3?', hint: null })
    const user = userEvent.setup()
    render(<QuizEditor quiz={draftQuiz} />)

    await user.click(await screen.findByRole('button', { name: 'Edit' }))
    const form = within(screen.getByRole('form', { name: 'Edit question' }))
    expect(form.getByLabelText(/Question/)).toHaveValue('What is 2+2?')
    expect(form.getByLabelText('Option 2 is correct')).toBeChecked()
    expect(form.getByLabelText('Hint (optional)')).toHaveValue('Count on your fingers')

    await user.clear(form.getByLabelText(/Question/))
    await user.type(form.getByLabelText(/Question/), 'What is 2+3?')
    await user.clear(form.getByLabelText('Hint (optional)'))
    await user.click(form.getByRole('button', { name: 'Save changes' }))

    expect(updateQuestion).toHaveBeenCalledWith(5, 1, {
      text: 'What is 2+3?',
      hint: null,
      options: [{ text: '3', correct: false }, { text: '4', correct: true }],
    }, {})
    expect(await screen.findByText('What is 2+3?')).toBeInTheDocument()
    expect(screen.queryByRole('form', { name: 'Edit question' })).not.toBeInTheDocument()
  })

  it('deletes a question after confirmation', async () => {
    getQuestionsWithAnswers.mockResolvedValue([existing])
    const user = userEvent.setup()
    render(<QuizEditor quiz={draftQuiz} />)

    await user.click(await screen.findByRole('button', { name: 'Delete' }))

    expect(deleteQuestion).toHaveBeenCalledWith(5, 1, {})
    expect(screen.queryByText('What is 2+2?')).not.toBeInTheDocument()
  })

  it('cannot publish a quiz without questions', async () => {
    render(<QuizEditor quiz={draftQuiz} />)

    expect(await screen.findByRole('button', { name: 'Publish quiz' })).toBeDisabled()
  })

  it('publishes the quiz and locks the questions', async () => {
    getQuestionsWithAnswers.mockResolvedValue([existing])
    const user = userEvent.setup()
    render(<QuizEditor quiz={draftQuiz} />)

    await user.click(await screen.findByRole('button', { name: 'Publish quiz' }))

    expect(publish).toHaveBeenCalledWith(5, {})
    expect(await screen.findByText(/Students can take this quiz/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument()
    expect(screen.queryByRole('form', { name: 'Add question' })).not.toBeInTheDocument()
  })

  it('does not publish when the teacher cancels the confirmation', async () => {
    getQuestionsWithAnswers.mockResolvedValue([existing])
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    const user = userEvent.setup()
    render(<QuizEditor quiz={draftQuiz} />)

    await user.click(await screen.findByRole('button', { name: 'Publish quiz' }))

    expect(publish).not.toHaveBeenCalled()
  })

  it('shows a published quiz as read-only', async () => {
    getQuestionsWithAnswers.mockResolvedValue([existing])
    render(<QuizEditor quiz={{ ...publishedQuiz, maxAttempts: 0 }} />)

    expect(await screen.findByText('What is 2+2?')).toBeInTheDocument()
    expect(screen.getByText(/Unlimited attempts per student/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Publish quiz' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: 'Student results' })).toBeInTheDocument()
  })

  it('does not show student results for a draft', async () => {
    render(<QuizEditor quiz={draftQuiz} />)

    expect(await screen.findByRole('button', { name: 'Publish quiz' })).toBeInTheDocument()
    expect(getResults).not.toHaveBeenCalled()
  })

  it('shows a published quiz whose deadline has passed as closed', async () => {
    getQuestionsWithAnswers.mockResolvedValue([existing])
    render(<QuizEditor quiz={{ ...publishedQuiz, deadline: '2026-11-20T12:00:00', closed: true }} />)

    expect(await screen.findByText('Closed')).toBeInTheDocument()
    expect(screen.getByText('The deadline has passed. Students can no longer submit attempts.')).toBeInTheDocument()
    expect(screen.queryByText(/Students can take this quiz/)).not.toBeInTheDocument()
  })

  it('lets the teacher hide results until they are published, then publish them', async () => {
    getQuestionsWithAnswers.mockResolvedValue([existing])
    const user = userEvent.setup()
    render(<QuizEditor quiz={publishedQuiz} />)

    const toggle = await screen.findByLabelText('Show results to students immediately')
    expect(toggle).toBeChecked()
    await user.click(toggle)

    expect(setResultVisibility).toHaveBeenCalledWith(5, false, {})
    expect(await screen.findByText(/Students can't see their scores yet/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Publish results' }))

    expect(publishGrades).toHaveBeenCalledWith(5)
    expect(await screen.findByText(/Results are published/)).toBeInTheDocument()
  })

  it('does not offer to publish results before the quiz itself is published', async () => {
    render(<QuizEditor quiz={{ ...draftQuiz, showResultsImmediately: false }} />)

    expect(await screen.findByText(/won't see their scores until you publish the results/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Publish results' })).not.toBeInTheDocument()
  })

  it('shows a quiz whose results are already published as published', async () => {
    render(<QuizEditor quiz={{ ...publishedQuiz, showResultsImmediately: false, resultsPublished: true }} />)

    expect(await screen.findByLabelText('Show results to students immediately')).not.toBeChecked()
    expect(screen.getByText(/Results are published/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Publish results' })).not.toBeInTheDocument()
  })

  it('shows the server error when saving fails', async () => {
    addQuestion.mockRejectedValue(new ApiError(409, 'This quiz is published', null))
    const user = userEvent.setup()
    render(<QuizEditor quiz={draftQuiz} />)

    const form = await fillNewQuestion(user, 'Q')
    await user.click(form.getByLabelText('Option 1 is correct'))
    await user.click(form.getByRole('button', { name: 'Save question' }))

    expect(await form.findByText('This quiz is published')).toBeInTheDocument()
  })
})
