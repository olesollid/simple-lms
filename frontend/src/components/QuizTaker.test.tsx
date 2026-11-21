import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../services/apiClient'
import QuizTaker from './QuizTaker'

const getQuestions = vi.hoisted(() => vi.fn())
const getOwnAttempts = vi.hoisted(() => vi.fn())
const submit = vi.hoisted(() => vi.fn())
vi.mock('../services', () => ({ quizzes: { getQuestions, getOwnAttempts, submit } }))

const questions = [
  { id: 1, text: 'What is 2+2?', position: 0, type: 'CHOICE',
    options: [{ id: 10, text: '3' }, { id: 11, text: '4' }] },
  { id: 2, text: 'Which are primes?', position: 1, type: 'CHOICE',
    options: [{ id: 20, text: '2' }, { id: 21, text: '5' }, { id: 22, text: '9' }] },
]

function grade(attemptNumber: number, correct: number, incorrectQuestionIds: number[]) {
  return {
    quizId: 5, studentEmail: 'student@example.com', attemptNumber, correctQuestions: correct,
    totalQuestions: 2, score: correct / 2, incorrectQuestionIds, gradedAt: '2026-11-20T10:00:00',
  }
}

const orderingQuestion = {
  id: 3, text: 'Order the steps', position: 2, type: 'ORDERING',
  // Served shuffled: the correct order is Plan, Build, Test.
  options: [{ id: 30, text: 'Test' }, { id: 31, text: 'Plan' }, { id: 32, text: 'Build' }],
}

const matchingQuestion = {
  id: 4, text: 'Match the capitals', position: 2, type: 'MATCHING',
  // Left-hand items in order, ids are positions; right-hand items arrive shuffled.
  options: [{ id: 0, text: 'Norway' }, { id: 1, text: 'France' }],
  matches: [{ id: 41, text: 'Paris' }, { id: 40, text: 'Oslo' }],
}

const fillInQuestion = {
  id: 5, text: 'The capital of ___ is ___.', position: 3, type: 'FILL_IN', options: [], matches: [],
}

const noAttempts = { attemptsUsed: 0, attemptsGraded: 0, maxAttempts: 3, deadline: null, closed: false, resultsVisible: true, best: null, latest: null, hints: {} }

describe('QuizTaker', () => {
  beforeEach(() => {
    getQuestions.mockReset().mockResolvedValue(questions)
    getOwnAttempts.mockReset().mockResolvedValue(noAttempts)
    submit.mockReset().mockResolvedValue({ attemptNumber: 1 })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('starts with the questions when the student has no attempts yet', async () => {
    render(<QuizTaker quizId={5} />)

    expect(await screen.findByText('What is 2+2?')).toBeInTheDocument()
    expect(screen.getByText(/Attempt 1 of 3/)).toBeInTheDocument()
    expect(screen.queryByText('Count on your fingers')).not.toBeInTheDocument()
  })

  it('explains that a question only scores when every correct answer is selected', async () => {
    render(<QuizTaker quizId={5} />)

    expect(await screen.findByText(/Each question is worth 1 point/)).toHaveTextContent(
      'counts only if it is completely right: every correct option selected and no wrong ones, the whole order right, every pair matched, or every blank filled in. A partly correct answer gives 0 points.')
  })

  it('offers no more attempts once the deadline has passed', async () => {
    getOwnAttempts.mockResolvedValue({
      attemptsUsed: 1, attemptsGraded: 1, resultsVisible: true, maxAttempts: 3, deadline: '2026-11-01T12:00:00', closed: true,
      best: grade(1, 1, [2]), latest: grade(1, 1, [2]),
    })
    render(<QuizTaker quizId={5} />)

    expect(await screen.findByText('The deadline has passed, so no more attempts are possible.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument()
    expect(screen.getByText('50%')).toBeInTheDocument()
  })

  it('does not open the questions when the deadline passed before the first attempt', async () => {
    getOwnAttempts.mockResolvedValue({ ...noAttempts, deadline: '2026-11-01T12:00:00', closed: true })
    render(<QuizTaker quizId={5} />)

    expect(await screen.findByText('The deadline has passed. You did not take this quiz.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Submit quiz' })).not.toBeInTheDocument()
    expect(screen.queryByText('What is 2+2?')).not.toBeInTheDocument()
  })

  it('submits the selected options per question', async () => {
    const user = userEvent.setup()
    render(<QuizTaker quizId={5} />)

    await user.click(await screen.findByLabelText('4'))
    await user.click(screen.getByLabelText('2'))
    await user.click(screen.getByLabelText('5'))
    await user.click(screen.getByLabelText('9'))
    await user.click(screen.getByLabelText('9'))
    await user.click(screen.getByRole('button', { name: 'Submit quiz' }))

    expect(submit).toHaveBeenCalledWith(5, { answers: { 1: [11], 2: [20, 21] } }, {})
  })

  it('shows the grade automatically once the attempt has been graded', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    render(<QuizTaker quizId={5} />)

    await user.click(await screen.findByLabelText('4'))
    await user.click(screen.getByRole('button', { name: 'Submit quiz' }))
    expect(await screen.findByText('Grading attempt 1…')).toBeInTheDocument()

    getOwnAttempts.mockResolvedValue({
      attemptsUsed: 1, attemptsGraded: 1, resultsVisible: true, maxAttempts: 3, best: grade(1, 1, [2]), latest: grade(1, 1, [2]),
    })
    await act(() => vi.advanceTimersByTimeAsync(1100))

    expect(await screen.findByRole('heading', { name: 'Attempt 1: 50%' })).toBeInTheDocument()
    expect(screen.getByText('1 of 3 attempts used')).toBeInTheDocument()
    expect(screen.getByText('Which are primes?')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument()
  })

  it('shows hints for the questions answered wrong, and again when retrying', async () => {
    getOwnAttempts.mockResolvedValue({
      attemptsUsed: 1, attemptsGraded: 1, resultsVisible: true, maxAttempts: 3, best: grade(1, 1, [1]), latest: grade(1, 1, [1]),
      hints: { 1: 'Count on your fingers' },
    })
    const user = userEvent.setup()
    render(<QuizTaker quizId={5} />)

    expect(await screen.findByText('Count on your fingers')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Try again' }))

    expect(screen.getByText(/Attempt 2 of 3/)).toBeInTheDocument()
    expect(screen.getByText('Count on your fingers')).toBeInTheDocument()
  })

  it('keeps the best attempt as the grade', async () => {
    getOwnAttempts.mockResolvedValue({
      attemptsUsed: 2, attemptsGraded: 2, resultsVisible: true, maxAttempts: 3, best: grade(1, 2, []), latest: grade(2, 1, [2]),
    })
    render(<QuizTaker quizId={5} />)

    expect(await screen.findByText('100%')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Attempt 2: 50%' })).toBeInTheDocument()
  })

  it('stops offering new attempts when they are used up', async () => {
    getOwnAttempts.mockResolvedValue({
      attemptsUsed: 3, attemptsGraded: 3, resultsVisible: true, maxAttempts: 3, best: grade(3, 2, []), latest: grade(3, 2, []),
    })
    render(<QuizTaker quizId={5} />)

    expect(await screen.findByText('You have used all your attempts.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument()
    expect(screen.getByText('Every question correct. Well done!')).toBeInTheDocument()
  })

  it('always offers another attempt when attempts are unlimited', async () => {
    getOwnAttempts.mockResolvedValue({
      attemptsUsed: 7, attemptsGraded: 7, resultsVisible: true, maxAttempts: 0, best: grade(7, 2, []), latest: grade(7, 2, []),
    })
    render(<QuizTaker quizId={5} />)

    expect(await screen.findByText('Unlimited attempts · 7 used')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument()
  })

  it('shows an error and keeps the answers when submitting fails', async () => {
    submit.mockRejectedValue(new ApiError(409, 'You have used all 3 attempts for this quiz', null))
    const user = userEvent.setup()
    render(<QuizTaker quizId={5} />)

    await user.click(await screen.findByLabelText('4'))
    await user.click(screen.getByRole('button', { name: 'Submit quiz' }))

    expect(await screen.findByText('You have used all 3 attempts for this quiz')).toBeInTheDocument()
    expect(screen.getByLabelText('4')).toBeChecked()
  })

  it('switches to the closed view when the deadline passes while the student is answering', async () => {
    submit.mockRejectedValue(
      new ApiError(403, 'The deadline has passed. This quiz no longer accepts attempts.', null))
    const user = userEvent.setup()
    render(<QuizTaker quizId={5} />)

    await user.click(await screen.findByLabelText('4'))
    getOwnAttempts.mockResolvedValue({ ...noAttempts, deadline: '2026-11-21T12:00:00', closed: true })
    await user.click(screen.getByRole('button', { name: 'Submit quiz' }))

    expect(await screen.findByText('The deadline has passed. You did not take this quiz.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Submit quiz' })).not.toBeInTheDocument()
    expect(screen.queryByText(/not enrolled/)).not.toBeInTheDocument()
  })

  it('shows the server message for a 403 instead of guessing the reason', async () => {
    submit.mockRejectedValue(new ApiError(403, 'Only enrolled students may take this quiz', null))
    const user = userEvent.setup()
    render(<QuizTaker quizId={5} />)

    await user.click(await screen.findByRole('button', { name: 'Submit quiz' }))

    expect(await screen.findByText('Only enrolled students may take this quiz')).toBeInTheDocument()
  })

  it('tells the student their results come later when the teacher keeps them hidden', async () => {
    getOwnAttempts.mockResolvedValue({ ...noAttempts, attemptsUsed: 1, attemptsGraded: 1, resultsVisible: false })
    render(<QuizTaker quizId={5} />)

    expect(await screen.findByText('Your answers are submitted. Your teacher will publish the results later.'))
      .toBeInTheDocument()
    expect(screen.queryByText(/Grading attempt/)).not.toBeInTheDocument()
    expect(screen.queryByText('Your grade')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument()
  })

  it('waits for grading after a submit even while results are hidden, then says the results come later', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    getOwnAttempts.mockResolvedValue({ ...noAttempts, resultsVisible: false })
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    render(<QuizTaker quizId={5} />)

    await user.click(await screen.findByLabelText('4'))
    await user.click(screen.getByRole('button', { name: 'Submit quiz' }))
    expect(await screen.findByText('Grading attempt 1…')).toBeInTheDocument()
    expect(screen.queryByText(/publish the results later/)).not.toBeInTheDocument()

    getOwnAttempts.mockResolvedValue({
      ...noAttempts, attemptsUsed: 1, attemptsGraded: 1, resultsVisible: false,
    })
    await act(() => vi.advanceTimersByTimeAsync(1100))

    expect(await screen.findByText('Your answers are submitted. Your teacher will publish the results later.'))
      .toBeInTheDocument()
    expect(screen.queryByText(/Grading attempt/)).not.toBeInTheDocument()
    expect(screen.queryByText('Your grade')).not.toBeInTheDocument()
  })

  it('shows a message when the quiz has no questions', async () => {
    getQuestions.mockResolvedValue([])
    render(<QuizTaker quizId={5} />)

    expect(await screen.findByText('This quiz has no questions yet.')).toBeInTheDocument()
  })

  it('shows a load error with a retry button', async () => {
    getQuestions.mockRejectedValue(new Error('offline'))
    render(<QuizTaker quizId={5} />)

    expect(await screen.findByText('Check your connection and try again.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument()
  })

  describe('ordering questions', () => {
    const optionTexts = () => screen.getAllByRole('listitem')
      .filter(item => item.classList.contains('quiz-order-item'))
      .map(item => item.querySelector('span')!.textContent)

    it('shows the options in the order served, with buttons to move them', async () => {
      getQuestions.mockResolvedValue([orderingQuestion])
      render(<QuizTaker quizId={5} />)

      expect(await screen.findByText('Order the steps')).toBeInTheDocument()
      expect(optionTexts()).toEqual(['Test', 'Plan', 'Build'])
      expect(screen.getByRole('button', { name: 'Move Test up' })).toBeDisabled()
      expect(screen.getByRole('button', { name: 'Move Build down' })).toBeDisabled()
      expect(screen.getByRole('button', { name: 'Move Plan up' })).toBeEnabled()
    })

    it('submits the order the student chose, next to the choice answers', async () => {
      getQuestions.mockResolvedValue([questions[0], orderingQuestion])
      const user = userEvent.setup()
      render(<QuizTaker quizId={5} />)

      await user.click(await screen.findByLabelText('4'))
      await user.click(screen.getByRole('button', { name: 'Move Plan up' }))
      await user.click(screen.getByRole('button', { name: 'Move Build up' }))
      expect(optionTexts()).toEqual(['Plan', 'Build', 'Test'])
      await user.click(screen.getByRole('button', { name: 'Submit quiz' }))

      expect(submit).toHaveBeenCalledWith(5, { answers: { 1: [11], 3: [31, 32, 30] } }, {})
    })

    it('submits an untouched ordering question in the order it was served', async () => {
      getQuestions.mockResolvedValue([orderingQuestion])
      const user = userEvent.setup()
      render(<QuizTaker quizId={5} />)

      await user.click(await screen.findByRole('button', { name: 'Submit quiz' }))

      expect(submit).toHaveBeenCalledWith(5, { answers: { 3: [30, 31, 32] } }, {})
    })

    it('explains how ordering questions are scored', async () => {
      getQuestions.mockResolvedValue([orderingQuestion])
      render(<QuizTaker quizId={5} />)

      expect(await screen.findByText(/Each question is worth 1 point/)).toHaveTextContent(/the whole order right/)
    })
  })

  describe('grading while results are hidden', () => {
    it('shows that an attempt is being graded, without the "results come later" message yet', async () => {
      getOwnAttempts.mockResolvedValue({
        ...noAttempts, attemptsUsed: 1, attemptsGraded: 0, resultsVisible: false,
      })
      render(<QuizTaker quizId={5} />)

      expect(await screen.findByText('Grading attempt 1…')).toBeInTheDocument()
      expect(screen.queryByText(/publish the results later/)).not.toBeInTheDocument()
    })

    it('never shows hints or scores while the results are hidden', async () => {
      getOwnAttempts.mockResolvedValue({
        ...noAttempts, attemptsUsed: 1, attemptsGraded: 1, resultsVisible: false,
      })
      render(<QuizTaker quizId={5} />)

      expect(await screen.findByText(/publish the results later/)).toBeInTheDocument()
      expect(screen.queryByText(/Hint:/)).not.toBeInTheDocument()
      expect(screen.queryByText(/%/)).not.toBeInTheDocument()
    })
  })

  describe('matching questions', () => {
    it('shows a dropdown of the right-hand items for every left-hand item', async () => {
      getQuestions.mockResolvedValue([matchingQuestion])
      render(<QuizTaker quizId={5} />)

      const norway = await screen.findByLabelText('Norway')
      expect(norway).toHaveValue('')
      expect(screen.getByLabelText('France')).toBeInTheDocument()
      expect(Array.from((norway as HTMLSelectElement).options).map(option => option.text))
        .toEqual(['Choose…', 'Paris', 'Oslo'])
    })

    it('submits one chosen right-hand id per left-hand item, in order', async () => {
      getQuestions.mockResolvedValue([matchingQuestion])
      const user = userEvent.setup()
      render(<QuizTaker quizId={5} />)

      await user.selectOptions(await screen.findByLabelText('Norway'), 'Oslo')
      await user.selectOptions(screen.getByLabelText('France'), 'Paris')
      await user.click(screen.getByRole('button', { name: 'Submit quiz' }))

      expect(submit).toHaveBeenCalledWith(5, { answers: { 4: [40, 41] } }, {})
    })

    it('lets the student change a choice before submitting', async () => {
      getQuestions.mockResolvedValue([matchingQuestion])
      const user = userEvent.setup()
      render(<QuizTaker quizId={5} />)

      await user.selectOptions(await screen.findByLabelText('Norway'), 'Paris')
      await user.selectOptions(screen.getByLabelText('Norway'), 'Oslo')
      await user.selectOptions(screen.getByLabelText('France'), 'Paris')
      await user.click(screen.getByRole('button', { name: 'Submit quiz' }))

      expect(submit).toHaveBeenCalledWith(5, { answers: { 4: [40, 41] } }, {})
    })

    it('sends no answer for a matching question that is only partly filled in', async () => {
      getQuestions.mockResolvedValue([questions[0], matchingQuestion])
      const user = userEvent.setup()
      render(<QuizTaker quizId={5} />)

      await user.click(await screen.findByLabelText('4'))
      await user.selectOptions(screen.getByLabelText('Norway'), 'Oslo')
      await user.click(screen.getByRole('button', { name: 'Submit quiz' }))

      expect(submit).toHaveBeenCalledWith(5, { answers: { 1: [11] } }, {})
    })

    it('clears the chosen matches after submitting', async () => {
      getQuestions.mockResolvedValue([matchingQuestion])
      getOwnAttempts.mockResolvedValue({ ...noAttempts, maxAttempts: 3 })
      const user = userEvent.setup()
      render(<QuizTaker quizId={5} />)

      await user.selectOptions(await screen.findByLabelText('Norway'), 'Oslo')
      await user.selectOptions(screen.getByLabelText('France'), 'Paris')
      getOwnAttempts.mockResolvedValue({
        ...noAttempts, attemptsUsed: 1, attemptsGraded: 1, resultsVisible: false,
      })
      await user.click(screen.getByRole('button', { name: 'Submit quiz' }))
      // Grading is polled once a second after submitting, so allow for one poll.
      await user.click(await screen.findByRole('button', { name: 'Try again' }, { timeout: 4000 }))

      expect(screen.getByLabelText('Norway')).toHaveValue('')
    })
  })

  describe('fill-in questions', () => {
    it('shows an input in place of every blank', async () => {
      getQuestions.mockResolvedValue([fillInQuestion])
      render(<QuizTaker quizId={5} />)

      expect(await screen.findByLabelText('Question 4, blank 1')).toBeInTheDocument()
      expect(screen.getByLabelText('Question 4, blank 2')).toBeInTheDocument()
      expect(screen.getByText(/The capital of/)).toBeInTheDocument()
      expect(screen.queryByText(/___/)).not.toBeInTheDocument()
    })

    it('submits what was typed, one entry per blank', async () => {
      getQuestions.mockResolvedValue([fillInQuestion])
      const user = userEvent.setup()
      render(<QuizTaker quizId={5} />)

      await user.type(await screen.findByLabelText('Question 4, blank 1'), 'Norway')
      await user.type(screen.getByLabelText('Question 4, blank 2'), 'Oslo')
      await user.click(screen.getByRole('button', { name: 'Submit quiz' }))

      expect(submit).toHaveBeenCalledWith(5,
        { answers: {}, textAnswers: { 5: ['Norway', 'Oslo'] } }, {})
    })

    it('sends an empty string for a blank that was left empty', async () => {
      getQuestions.mockResolvedValue([fillInQuestion])
      const user = userEvent.setup()
      render(<QuizTaker quizId={5} />)

      await user.type(await screen.findByLabelText('Question 4, blank 2'), 'Oslo')
      await user.click(screen.getByRole('button', { name: 'Submit quiz' }))

      expect(submit).toHaveBeenCalledWith(5,
        { answers: {}, textAnswers: { 5: ['', 'Oslo'] } }, {})
    })

    it('sends choice and typed answers together', async () => {
      getQuestions.mockResolvedValue([questions[0], fillInQuestion])
      const user = userEvent.setup()
      render(<QuizTaker quizId={5} />)

      await user.click(await screen.findByLabelText('4'))
      await user.type(screen.getByLabelText('Question 4, blank 1'), 'Norway')
      await user.click(screen.getByRole('button', { name: 'Submit quiz' }))

      expect(submit).toHaveBeenCalledWith(5,
        { answers: { 1: [11] }, textAnswers: { 5: ['Norway', ''] } }, {})
    })

    it('never sends a typed-answers entry when the quiz has no fill-in question', async () => {
      const user = userEvent.setup()
      render(<QuizTaker quizId={5} />)

      await user.click(await screen.findByLabelText('4'))
      await user.click(screen.getByRole('button', { name: 'Submit quiz' }))

      expect(submit.mock.calls[0][1]).not.toHaveProperty('textAnswers')
    })
  })
})
