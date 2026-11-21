import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import QuizResults from './QuizResults'

const getResults = vi.hoisted(() => vi.fn())
vi.mock('../services', () => ({ quizzes: { getResults } }))

const best = (score: number, correct: number) => ({
  quizId: 5, studentEmail: 'x', attemptNumber: 1, correctQuestions: correct, totalQuestions: 4,
  score, incorrectQuestionIds: [], gradedAt: '2026-11-20T10:00:00',
})

describe('QuizResults', () => {
  beforeEach(() => {
    getResults.mockReset()
  })

  it('lists every student with attempts and best score, plus a summary', async () => {
    getResults.mockResolvedValue([
      { email: 'ada@example.com', name: 'Ada', attemptsUsed: 2, best: best(0.75, 3) },
      { email: 'bo@example.com', name: 'Bo', attemptsUsed: 1, best: best(0.25, 1) },
      { email: 'cy@example.com', name: 'Cy', attemptsUsed: 0, best: null },
    ])
    render(<QuizResults quizId={5} maxAttempts={3} />)

    const ada = (await screen.findByText('Ada')).closest('tr')!
    expect(within(ada).getByText('2 / 3')).toBeInTheDocument()
    expect(within(ada).getByText('75% (3/4)')).toBeInTheDocument()
    expect(within(screen.getByText('Cy').closest('tr')!).getByText('Not taken')).toBeInTheDocument()
    expect(screen.getByText(/2 of 3 students have taken the quiz · average best score 50%/)).toBeInTheDocument()
  })

  it('shows attempts without a limit for unlimited quizzes, and grading in progress', async () => {
    getResults.mockResolvedValue([{ email: 'ada@example.com', name: 'Ada', attemptsUsed: 4, best: null }])
    render(<QuizResults quizId={5} maxAttempts={0} />)

    const row = (await screen.findByText('Ada')).closest('tr')!
    expect(within(row).getByText('4')).toBeInTheDocument()
    expect(within(row).getByText('Grading…')).toBeInTheDocument()
  })

  it('refreshes the results', async () => {
    getResults.mockResolvedValueOnce([])
      .mockResolvedValueOnce([{ email: 'ada@example.com', name: 'Ada', attemptsUsed: 1, best: best(1, 4) }])
    const user = userEvent.setup()
    render(<QuizResults quizId={5} maxAttempts={1} />)

    expect(await screen.findByText('No students are enrolled in this course yet.')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Refresh' }))

    expect(await screen.findByText('100% (4/4)')).toBeInTheDocument()
  })

  it('shows an error with a refresh button', async () => {
    getResults.mockRejectedValue(new Error('offline'))
    render(<QuizResults quizId={5} maxAttempts={1} />)

    expect(await screen.findByText('Check your connection and try again.')).toBeInTheDocument()
  })
})
