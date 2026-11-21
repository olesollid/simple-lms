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
const updateSettings = vi.hoisted(() => vi.fn())
const getResults = vi.hoisted(() => vi.fn())
const setResultVisibility = vi.hoisted(() => vi.fn())
const publishGrades = vi.hoisted(() => vi.fn())
vi.mock('../services', () => ({
  quizzes: {
    getQuestionsWithAnswers, addQuestion, updateQuestion, deleteQuestion, publish, updateSettings,
    getResults, setResultVisibility,
  },
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
  id: 1, text: 'What is 2+2?', position: 0, hint: 'Count on your fingers', type: 'CHOICE' as const,
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
    updateSettings.mockReset().mockResolvedValue({ ...draftQuiz })
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
    expect(screen.getByText(/Each question is worth 1 point/)).toBeInTheDocument()
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
      type: 'CHOICE',
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
      type: 'CHOICE',
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

  describe('quiz settings', () => {
    it('starts from the quiz\'s current attempt limit', async () => {
      render(<QuizEditor quiz={{ ...draftQuiz, maxAttempts: 3 }} />)

      const form = within(await screen.findByRole('form', { name: 'Quiz settings' }))
      expect(form.getByLabelText('Maximum attempts')).toHaveValue(3)
      expect(form.queryByLabelText(/hint/i)).not.toBeInTheDocument()
    })

    it('saves the attempt limit and shows it in the status', async () => {
      const user = userEvent.setup()
      render(<QuizEditor quiz={publishedQuiz} />)

      const form = within(await screen.findByRole('form', { name: 'Quiz settings' }))
      await user.clear(form.getByLabelText('Maximum attempts'))
      await user.type(form.getByLabelText('Maximum attempts'), '5')
      await user.click(form.getByRole('button', { name: 'Save settings' }))

      expect(updateSettings).toHaveBeenCalledWith(5, { maxAttempts: 5 }, {})
      expect(await screen.findByText('Settings saved.')).toBeInTheDocument()
      expect(screen.getByText(/5 attempts per student/)).toBeInTheDocument()
    })

    it('treats 0 as unlimited attempts', async () => {
      const user = userEvent.setup()
      render(<QuizEditor quiz={publishedQuiz} />)

      const form = within(await screen.findByRole('form', { name: 'Quiz settings' }))
      await user.clear(form.getByLabelText('Maximum attempts'))
      await user.type(form.getByLabelText('Maximum attempts'), '0')
      await user.click(form.getByRole('button', { name: 'Save settings' }))

      expect(updateSettings).toHaveBeenCalledWith(5, { maxAttempts: 0 }, {})
      expect(await screen.findByText(/Unlimited attempts per student/)).toBeInTheDocument()
    })

    it('rejects a limit that is not a whole number without calling the server', async () => {
      const user = userEvent.setup()
      render(<QuizEditor quiz={publishedQuiz} />)

      const form = within(await screen.findByRole('form', { name: 'Quiz settings' }))
      await user.clear(form.getByLabelText('Maximum attempts'))
      await user.click(form.getByRole('button', { name: 'Save settings' }))

      expect(form.getByText(/must be a whole number/)).toBeInTheDocument()
      expect(updateSettings).not.toHaveBeenCalled()
    })

    it('shows the server error when saving the settings fails', async () => {
      updateSettings.mockRejectedValue(new ApiError(409, 'Settings could not be changed', null))
      const user = userEvent.setup()
      render(<QuizEditor quiz={publishedQuiz} />)

      await user.click(await screen.findByRole('button', { name: 'Save settings' }))

      expect(await screen.findByText('Settings could not be changed')).toBeInTheDocument()
      expect(screen.queryByText('Settings saved.')).not.toBeInTheDocument()
    })

    it('keeps the result visibility controls next to the attempt limit', async () => {
      render(<QuizEditor quiz={publishedQuiz} />)

      expect(await screen.findByRole('form', { name: 'Quiz settings' })).toBeInTheDocument()
      expect(screen.getByLabelText('Show results to students immediately')).toBeChecked()
    })
  })

  describe('ordering questions', () => {
    it('lets the teacher write an ordering question as a list in the correct order', async () => {
      addQuestion.mockResolvedValue({ ...existing, id: 2, type: 'ORDERING' })
      const user = userEvent.setup()
      render(<QuizEditor quiz={draftQuiz} />)

      const form = await fillNewQuestion(user, 'Order the steps')
      await user.click(form.getByLabelText(/Ordering: students put the options/))

      expect(form.queryByLabelText('Option 1 is correct')).not.toBeInTheDocument()
      expect(form.getByText(/Options, in the correct order/)).toBeInTheDocument()
      await user.click(form.getByRole('button', { name: 'Save question' }))

      expect(addQuestion).toHaveBeenCalledWith(5, {
        text: 'Order the steps',
        hint: null,
        type: 'ORDERING',
        options: [{ text: 'Bergen', correct: false }, { text: 'Oslo', correct: false }],
      }, {})
    })

    it('still needs two options for an ordering question', async () => {
      const user = userEvent.setup()
      render(<QuizEditor quiz={draftQuiz} />)

      const form = await fillNewQuestion(user, 'Order')
      await user.click(form.getByLabelText(/Ordering: students put the options/))
      await user.clear(form.getByLabelText('Option 2'))
      await user.click(form.getByRole('button', { name: 'Save question' }))

      expect(form.getByText('Add at least two answer options.')).toBeInTheDocument()
      expect(addQuestion).not.toHaveBeenCalled()
    })

    it('shows an existing ordering question as the correct order, without correct marks', async () => {
      getQuestionsWithAnswers.mockResolvedValue([{
        id: 3, text: 'Order the steps', position: 0, hint: null, type: 'ORDERING',
        options: [
          { id: 30, text: 'Plan', correct: true }, { id: 31, text: 'Build', correct: true },
          { id: 32, text: 'Test', correct: true },
        ],
      }])
      render(<QuizEditor quiz={draftQuiz} />)

      expect(await screen.findByText(/Correct order/)).toBeInTheDocument()
      expect(screen.getAllByRole('listitem').filter(item => item.closest('ol.quiz-order-answer'))
        .map(item => item.textContent)).toEqual(['Plan', 'Build', 'Test'])
      expect(screen.queryByText(/✓ Correct/)).not.toBeInTheDocument()
    })

    it('starts the edit form of an ordering question in ordering mode', async () => {
      getQuestionsWithAnswers.mockResolvedValue([{
        id: 3, text: 'Order the steps', position: 0, hint: null, type: 'ORDERING',
        options: [{ id: 30, text: 'Plan', correct: true }, { id: 31, text: 'Build', correct: true }],
      }])
      const user = userEvent.setup()
      render(<QuizEditor quiz={draftQuiz} />)

      await user.click(await screen.findByRole('button', { name: 'Edit' }))
      const form = within(screen.getByRole('form', { name: 'Edit question' }))

      expect(form.getByLabelText(/Ordering: students put the options/)).toBeChecked()
      expect(form.queryByLabelText('Option 1 is correct')).not.toBeInTheDocument()
    })
  })

  describe('matching questions', () => {
    async function openMatching(user: ReturnType<typeof userEvent.setup>) {
      render(<QuizEditor quiz={draftQuiz} />)
      const form = within(await screen.findByRole('form', { name: 'Add question' }))
      await user.type(form.getByLabelText(/Question/), 'Match the capitals')
      await user.click(form.getByLabelText(/Matching: students match/))
      return form
    }

    it('shows a left and a right field for every pair', async () => {
      const form = await openMatching(userEvent.setup())

      expect(form.getByLabelText('Pair 1 left')).toBeInTheDocument()
      expect(form.getByLabelText('Pair 1 right')).toBeInTheDocument()
      expect(form.getByLabelText('Pair 2 left')).toBeInTheDocument()
      expect(form.queryByLabelText('Option 1 is correct')).not.toBeInTheDocument()
      expect(form.getByRole('button', { name: 'Add pair' })).toBeInTheDocument()
    })

    it('saves the pairs with the right-hand text as matchText', async () => {
      addQuestion.mockResolvedValue({ ...existing, id: 2, type: 'MATCHING' })
      const user = userEvent.setup()
      const form = await openMatching(user)

      await user.type(form.getByLabelText('Pair 1 left'), 'Norway')
      await user.type(form.getByLabelText('Pair 1 right'), 'Oslo')
      await user.type(form.getByLabelText('Pair 2 left'), 'France')
      await user.type(form.getByLabelText('Pair 2 right'), 'Paris')
      await user.click(form.getByRole('button', { name: 'Save question' }))

      expect(addQuestion).toHaveBeenCalledWith(5, {
        text: 'Match the capitals',
        hint: null,
        type: 'MATCHING',
        options: [
          { text: 'Norway', correct: false, matchText: 'Oslo' },
          { text: 'France', correct: false, matchText: 'Paris' },
        ],
      }, {})
    })

    it('needs both sides of every pair and at least two pairs', async () => {
      const user = userEvent.setup()
      const form = await openMatching(user)

      await user.type(form.getByLabelText('Pair 1 left'), 'Norway')
      await user.type(form.getByLabelText('Pair 2 left'), 'France')
      await user.type(form.getByLabelText('Pair 2 right'), 'Paris')
      await user.click(form.getByRole('button', { name: 'Save question' }))
      expect(form.getByText('Fill in both sides of every pair.')).toBeInTheDocument()

      await user.clear(form.getByLabelText('Pair 1 left'))
      await user.click(form.getByRole('button', { name: 'Save question' }))
      expect(form.getByText('Add at least two pairs.')).toBeInTheDocument()
      expect(addQuestion).not.toHaveBeenCalled()
    })

    it('shows an existing matching question as pairs and edits it in matching mode', async () => {
      getQuestionsWithAnswers.mockResolvedValue([{
        id: 4, text: 'Match the capitals', position: 0, hint: null, type: 'MATCHING',
        options: [
          { id: 40, text: 'Norway', correct: true, matchText: 'Oslo' },
          { id: 41, text: 'France', correct: true, matchText: 'Paris' },
        ],
      }])
      const user = userEvent.setup()
      render(<QuizEditor quiz={draftQuiz} />)

      expect(await screen.findByText(/Matching question/)).toBeInTheDocument()
      expect(screen.getAllByRole('listitem').filter(item => item.closest('ul.quiz-pair-answer'))
        .map(item => item.textContent)).toEqual(['Norway → Oslo', 'France → Paris'])

      await user.click(screen.getByRole('button', { name: 'Edit' }))
      const form = within(screen.getByRole('form', { name: 'Edit question' }))
      expect(form.getByLabelText(/Matching: students match/)).toBeChecked()
      expect(form.getByLabelText('Pair 1 left')).toHaveValue('Norway')
      expect(form.getByLabelText('Pair 1 right')).toHaveValue('Oslo')
    })
  })

  describe('fill-in questions', () => {
    async function openFillIn(user: ReturnType<typeof userEvent.setup>, questionText: string) {
      render(<QuizEditor quiz={draftQuiz} />)
      const form = within(await screen.findByRole('form', { name: 'Add question' }))
      await user.type(form.getByLabelText(/Question/), questionText)
      await user.click(form.getByLabelText(/Fill in the blanks: students type/))
      return form
    }

    it('explains how to mark blanks and separate alternative answers', async () => {
      const form = await openFillIn(userEvent.setup(), 'x')

      expect(form.getByText(/Type ___ \(three underscores\)/)).toBeInTheDocument()
      expect(form.getByText(/Separate answers that are also accepted with \|/)).toBeInTheDocument()
      expect(form.getByLabelText('Blank 1 accepted answers')).toBeInTheDocument()
      expect(form.getByRole('button', { name: 'Add blank' })).toBeInTheDocument()
    })

    it('saves one entry per blank with its accepted answers', async () => {
      addQuestion.mockResolvedValue({ ...existing, id: 2, type: 'FILL_IN' })
      const user = userEvent.setup()
      const form = await openFillIn(user, 'The capital of ___ is ___.')

      await user.type(form.getByLabelText('Blank 1 accepted answers'), 'Norway | Norge')
      await user.type(form.getByLabelText('Blank 2 accepted answers'), 'Oslo')
      await user.click(form.getByRole('button', { name: 'Save question' }))

      expect(addQuestion).toHaveBeenCalledWith(5, {
        text: 'The capital of ___ is ___.',
        hint: null,
        type: 'FILL_IN',
        options: [{ text: 'Norway | Norge', correct: false }, { text: 'Oslo', correct: false }],
      }, {})
    })

    it('refuses a question whose number of blanks does not match the answers given', async () => {
      const user = userEvent.setup()
      const form = await openFillIn(user, 'The capital of ___ is ___.')

      await user.type(form.getByLabelText('Blank 1 accepted answers'), 'Norway')
      await user.type(form.getByLabelText('Blank 2 accepted answers'), 'Oslo')
      await user.click(form.getByRole('button', { name: 'Add blank' }))
      await user.type(form.getByLabelText('Blank 3 accepted answers'), 'extra')
      await user.click(form.getByRole('button', { name: 'Save question' }))

      expect(form.getByText(/The question has 2 blanks \(___\) but you gave 3 answers/)).toBeInTheDocument()
      expect(addQuestion).not.toHaveBeenCalled()
    })

    it('needs at least one blank answer, and can go down to a single blank', async () => {
      const user = userEvent.setup()
      const form = await openFillIn(user, 'Capital of ___')

      await user.click(form.getAllByRole('button', { name: 'Remove' })[0])
      expect(form.queryByRole('button', { name: 'Remove' })).not.toBeInTheDocument()
      await user.click(form.getByRole('button', { name: 'Save question' }))

      expect(form.getByText('Add the accepted answer for each blank.')).toBeInTheDocument()
      expect(addQuestion).not.toHaveBeenCalled()
    })

    it('shows an existing fill-in question with its accepted answers', async () => {
      getQuestionsWithAnswers.mockResolvedValue([{
        id: 5, text: 'The capital of ___ is ___.', position: 0, hint: null, type: 'FILL_IN',
        options: [
          { id: 50, text: 'Norway|Norge', correct: true }, { id: 51, text: 'Oslo', correct: true },
        ],
      }])
      render(<QuizEditor quiz={draftQuiz} />)

      expect(await screen.findByText(/Fill-in question/)).toBeInTheDocument()
      expect(screen.getAllByRole('listitem').filter(item => item.closest('ol.quiz-blank-answer'))
        .map(item => item.textContent)).toEqual(['Norway | Norge', 'Oslo'])
    })
  })
})
