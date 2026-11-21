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
  { id: 1, text: 'What is 2+2?', position: 0,
    options: [{ id: 10, text: '3' }, { id: 11, text: '4' }] },
  { id: 2, text: 'Which are primes?', position: 1,
    options: [{ id: 20, text: '2' }, { id: 21, text: '5' }, { id: 22, text: '9' }] },
]

function grade(attemptNumber: number, correct: number, incorrectQuestionIds: number[]) {
  return {
    quizId: 5, studentEmail: 'student@example.com', attemptNumber, correctQuestions: correct,
    totalQuestions: 2, score: correct / 2, incorrectQuestionIds, gradedAt: '2026-11-20T10:00:00',
  }
}

const noAttempts = { attemptsUsed: 0, maxAttempts: 3, deadline: null, closed: false, resultsVisible: true, best: null, latest: null, hints: {} }

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
      'Select every correct answer and no wrong ones to get the point. A partly correct answer gives 0 points.')
  })

  it('offers no more attempts once the deadline has passed', async () => {
    getOwnAttempts.mockResolvedValue({
      attemptsUsed: 1, maxAttempts: 3, deadline: '2026-11-01T12:00:00', closed: true,
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
      attemptsUsed: 1, maxAttempts: 3, best: grade(1, 1, [2]), latest: grade(1, 1, [2]),
    })
    await act(() => vi.advanceTimersByTimeAsync(1100))

    expect(await screen.findByRole('heading', { name: 'Attempt 1: 50%' })).toBeInTheDocument()
    expect(screen.getByText('1 of 3 attempts used')).toBeInTheDocument()
    expect(screen.getByText('Which are primes?')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument()
  })

  it('shows hints for the questions answered wrong, and again when retrying', async () => {
    getOwnAttempts.mockResolvedValue({
      attemptsUsed: 1, maxAttempts: 3, best: grade(1, 1, [1]), latest: grade(1, 1, [1]),
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
      attemptsUsed: 2, maxAttempts: 3, best: grade(1, 2, []), latest: grade(2, 1, [2]),
    })
    render(<QuizTaker quizId={5} />)

    expect(await screen.findByText('100%')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Attempt 2: 50%' })).toBeInTheDocument()
  })

  it('stops offering new attempts when they are used up', async () => {
    getOwnAttempts.mockResolvedValue({
      attemptsUsed: 3, maxAttempts: 3, best: grade(3, 2, []), latest: grade(3, 2, []),
    })
    render(<QuizTaker quizId={5} />)

    expect(await screen.findByText('You have used all your attempts.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument()
    expect(screen.getByText('Every question correct. Well done!')).toBeInTheDocument()
  })

  it('always offers another attempt when attempts are unlimited', async () => {
    getOwnAttempts.mockResolvedValue({
      attemptsUsed: 7, maxAttempts: 0, best: grade(7, 2, []), latest: grade(7, 2, []),
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
    getOwnAttempts.mockResolvedValue({ ...noAttempts, attemptsUsed: 1, resultsVisible: false })
    render(<QuizTaker quizId={5} />)

    expect(await screen.findByText('Your answers are submitted. Your teacher will publish the results later.'))
      .toBeInTheDocument()
    expect(screen.queryByText(/Grading attempt/)).not.toBeInTheDocument()
    expect(screen.queryByText('Your grade')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument()
  })

  it('does not wait for grading after a submit while results are hidden', async () => {
    getOwnAttempts.mockResolvedValue({ ...noAttempts, resultsVisible: false })
    const user = userEvent.setup()
    render(<QuizTaker quizId={5} />)

    await user.click(await screen.findByLabelText('4'))
    await user.click(screen.getByRole('button', { name: 'Submit quiz' }))

    expect(await screen.findByText('Your answers are submitted. Your teacher will publish the results later.'))
      .toBeInTheDocument()
    expect(screen.queryByText(/Grading attempt/)).not.toBeInTheDocument()
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
})
