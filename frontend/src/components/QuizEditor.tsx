import { useEffect, useState } from 'react'
import { quizzes, submissions } from '../services'
import { ApiError, ApiResponseError } from '../services/apiClient'
import Button from './ui/Button'
import ErrorMessage from './ui/ErrorMessage'
import LoadingIndicator from './ui/LoadingIndicator'
import QuizResults from './QuizResults'
import type { ContentResponse } from '../types/content'
import type { QuestionRequest, QuestionWithAnswers } from '../types/quiz'

type EditorState =
  | { status: 'loading' }
  | { status: 'success'; questions: QuestionWithAnswers[] }
  | { status: 'error'; message: string }

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 403) return 'Only teachers in this course can edit the quiz.'
    return error.message
  }
  if (error instanceof ApiResponseError) return 'The server returned an unexpected response.'
  return 'Check your connection and try again.'
}

function attemptsLabel(maxAttempts: number | null | undefined): string {
  if (!maxAttempts) return 'Unlimited attempts'
  return maxAttempts === 1 ? '1 attempt' : `${maxAttempts} attempts`
}

/**
 * Teacher view of a quiz. While it is a draft, questions can be added, edited and removed, and
 * students cannot see it. Publishing makes it available and freezes the questions, so every
 * attempt is graded against the same answers.
 */
export default function QuizEditor({ quiz }: { quiz: ContentResponse }) {
  const [state, setState] = useState<EditorState>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)
  const [published, setPublished] = useState(quiz.published === true)
  const closed = quiz.closed === true
  const [showImmediately, setShowImmediately] = useState(quiz.showResultsImmediately !== false)
  const [resultsPublished, setResultsPublished] = useState(quiz.resultsPublished === true)
  const [savingVisibility, setSavingVisibility] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [publishing, setPublishing] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    quizzes.getQuestionsWithAnswers(quiz.id, { signal: controller.signal }).then(
      questions => {
        if (!controller.signal.aborted) setState({ status: 'success', questions })
      },
      error => {
        if (!controller.signal.aborted) setState({ status: 'error', message: errorMessage(error) })
      },
    )
    return () => controller.abort()
  }, [quiz.id, attempt])

  function reload() {
    setState({ status: 'loading' })
    setAttempt(previous => previous + 1)
  }

  function replaceQuestions(update: (questions: QuestionWithAnswers[]) => QuestionWithAnswers[]) {
    setState(previous => previous.status === 'success'
      ? { ...previous, questions: update(previous.questions) }
      : previous)
  }

  async function addQuestion(request: QuestionRequest) {
    const question = await quizzes.addQuestion(quiz.id, request, {})
    replaceQuestions(questions => [...questions, question])
  }

  async function updateQuestion(questionId: number, request: QuestionRequest) {
    const updated = await quizzes.updateQuestion(quiz.id, questionId, request, {})
    replaceQuestions(questions => questions.map(question => (question.id === questionId ? updated : question)))
    setEditingId(null)
  }

  async function deleteQuestion(questionId: number) {
    if (!window.confirm('Delete this question?')) return
    setActionError(null)
    try {
      await quizzes.deleteQuestion(quiz.id, questionId, {})
      replaceQuestions(questions => questions.filter(question => question.id !== questionId))
    } catch (error) {
      setActionError(errorMessage(error))
    }
  }

  async function publish() {
    if (!window.confirm('Publish this quiz? The questions can no longer be changed.')) return
    setPublishing(true)
    setActionError(null)
    try {
      await quizzes.publish(quiz.id, {})
      setPublished(true)
      setEditingId(null)
    } catch (error) {
      setActionError(errorMessage(error))
    } finally {
      setPublishing(false)
    }
  }

  async function changeResultVisibility(immediately: boolean) {
    setSavingVisibility(true)
    setActionError(null)
    try {
      await quizzes.setResultVisibility(quiz.id, immediately, {})
      setShowImmediately(immediately)
    } catch (error) {
      setActionError(errorMessage(error))
    } finally {
      setSavingVisibility(false)
    }
  }

  async function publishResults() {
    setSavingVisibility(true)
    setActionError(null)
    try {
      await submissions.publishGrades(quiz.id)
      setResultsPublished(true)
    } catch (error) {
      setActionError(errorMessage(error))
    } finally {
      setSavingVisibility(false)
    }
  }

  if (state.status === 'loading') return <LoadingIndicator message="Loading questions…" />

  if (state.status === 'error') {
    return (
      <ErrorMessage title="Unable to load the quiz" message={state.message}>
        <button className="refresh-button" type="button" onClick={reload}>Try again</button>
      </ErrorMessage>
    )
  }

  const { questions } = state

  return (
    <section className="quiz-editor" aria-label="Quiz questions">
      <div className={`quiz-status ${!published ? 'quiz-status-draft' : closed ? 'quiz-status-closed' : 'quiz-status-published'}`} role="status">
        <strong>{!published ? 'Draft' : closed ? 'Closed' : 'Published'}</strong>
        <span>
          {!published
            ? 'Students cannot see this quiz yet. You can change the questions until you publish it.'
            : closed
              ? 'The deadline has passed. Students can no longer submit attempts.'
              : 'Students can take this quiz. The questions are locked so every attempt is graded the same way.'}
        </span>
        <span>{attemptsLabel(quiz.maxAttempts)} per student.</span>
        <label className="ui-checkbox-field">
          <input
            type="checkbox"
            checked={showImmediately}
            disabled={savingVisibility}
            onChange={event => changeResultVisibility(event.target.checked)}
          />
          Show results to students immediately
        </label>
        {!showImmediately && !published && (
          <span>Students won't see their scores until you publish the results, which you can do once the quiz is published.</span>
        )}
        {!showImmediately && published && (resultsPublished ? (
          <span>Results are published: students can see their scores and hints.</span>
        ) : (
          <span className="quiz-results-hidden">
            Students can't see their scores yet.{' '}
            <Button variant="secondary" onClick={publishResults} loading={savingVisibility}>
              Publish results
            </Button>
          </span>
        ))}
        <span>
          Each question is worth 1 point, given only when the student selects every correct option
          and no wrong ones. Partly correct answers give 0 points.
        </span>
        {!published && (
          <Button onClick={publish} loading={publishing} disabled={questions.length === 0}>
            Publish quiz
          </Button>
        )}
      </div>
      {actionError && <ErrorMessage message={actionError} />}

      <h2>Questions ({questions.length})</h2>
      {questions.length === 0 ? (
        <p className="module-placeholder">No questions yet. Add the first one below.</p>
      ) : (
        <ol className="quiz-question-list">
          {questions.map(question => (
            <li key={question.id} className="quiz-question">
              {editingId === question.id ? (
                <QuestionForm
                  initial={question}
                  title="Edit question"
                  submitLabel="Save changes"
                  onSave={request => updateQuestion(question.id, request)}
                  onCancel={() => setEditingId(null)}
                />
              ) : (
                <>
                  <p className="quiz-question-text">{question.text}</p>
                  <ul className="quiz-option-list">
                    {question.options.map(option => (
                      <li key={option.id} className={option.correct ? 'quiz-option-correct' : undefined}>
                        {option.text}
                        {option.correct && <span className="quiz-correct-badge"> ✓ Correct</span>}
                      </li>
                    ))}
                  </ul>
                  {question.hint && <p className="quiz-hint"><strong>Hint:</strong> {question.hint}</p>}
                  {!published && (
                    <div className="page-heading-actions">
                      <Button variant="secondary" onClick={() => setEditingId(question.id)}>Edit</Button>
                      <Button variant="danger" onClick={() => deleteQuestion(question.id)}>Delete</Button>
                    </div>
                  )}
                </>
              )}
            </li>
          ))}
        </ol>
      )}

      {published && <QuizResults quizId={quiz.id} maxAttempts={quiz.maxAttempts ?? null} />}

      {!published && (
        <div className="quiz-question-form-card">
          <QuestionForm title="Add question" submitLabel="Save question" onSave={addQuestion} resetOnSave />
        </div>
      )}
    </section>
  )
}

interface OptionDraft {
  text: string
  correct: boolean
}

const emptyOptions = (): OptionDraft[] => [{ text: '', correct: false }, { text: '', correct: false }]

/** Add/edit form for one question: text, optional hint, and 2+ options with the correct ones ticked. */
function QuestionForm({ initial, title, submitLabel, onSave, onCancel, resetOnSave = false }: {
  initial?: QuestionWithAnswers
  title: string
  submitLabel: string
  onSave: (request: QuestionRequest) => Promise<void>
  onCancel?: () => void
  resetOnSave?: boolean
}) {
  const idPrefix = initial ? `question-${initial.id}` : 'new-question'
  const [text, setText] = useState(initial?.text ?? '')
  const [hint, setHint] = useState(initial?.hint ?? '')
  const [options, setOptions] = useState<OptionDraft[]>(
    initial ? initial.options.map(option => ({ text: option.text, correct: option.correct })) : emptyOptions())
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  function updateOption(index: number, change: Partial<OptionDraft>) {
    setOptions(previous => previous.map((option, i) => (i === index ? { ...option, ...change } : option)))
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    const filled = options.map(option => ({ ...option, text: option.text.trim() }))
      .filter(option => option.text)
    if (filled.length < 2) {
      setError('Add at least two answer options.')
      return
    }
    if (!filled.some(option => option.correct)) {
      setError('Mark at least one option as correct.')
      return
    }
    setSaving(true)
    setError(null)
    try {
      await onSave({ text: text.trim(), hint: hint.trim() || null, options: filled })
      if (resetOnSave) {
        setText('')
        setHint('')
        setOptions(emptyOptions())
      }
    } catch (saveError) {
      setError(errorMessage(saveError))
    } finally {
      setSaving(false)
    }
  }

  return (
    <form className="quiz-question-form" onSubmit={handleSubmit} aria-label={title}>
      <h3>{title}</h3>
      <div className="ui-field">
        <label className="ui-label" htmlFor={`${idPrefix}-text`}>
          Question<span className="ui-required" aria-hidden="true"> *</span>
        </label>
        <textarea
          id={`${idPrefix}-text`}
          className="ui-input"
          rows={2}
          value={text}
          onChange={event => setText(event.target.value)}
          required
        />
      </div>
      <fieldset className="quiz-option-fields">
        <legend className="ui-label">Answer options (tick the correct ones)</legend>
        {options.map((option, index) => (
          <div key={index} className="quiz-option-field">
            <input
              type="checkbox"
              aria-label={`Option ${index + 1} is correct`}
              checked={option.correct}
              onChange={event => updateOption(index, { correct: event.target.checked })}
            />
            <input
              className="ui-input"
              aria-label={`Option ${index + 1}`}
              value={option.text}
              onChange={event => updateOption(index, { text: event.target.value })}
            />
            {options.length > 2 && (
              <Button
                variant="secondary"
                onClick={() => setOptions(previous => previous.filter((_, i) => i !== index))}
              >
                Remove
              </Button>
            )}
          </div>
        ))}
        <Button variant="secondary" onClick={() => setOptions(previous => [...previous, { text: '', correct: false }])}>
          Add option
        </Button>
      </fieldset>
      <div className="ui-field">
        <label className="ui-label" htmlFor={`${idPrefix}-hint`}>Hint (optional)</label>
        <textarea
          id={`${idPrefix}-hint`}
          className="ui-input"
          rows={2}
          value={hint}
          onChange={event => setHint(event.target.value)}
        />
        <p className="ui-hint">Shown to students who answer this question wrong.</p>
      </div>
      {error && <ErrorMessage message={error} />}
      <div className="page-heading-actions">
        <Button type="submit" loading={saving}>{submitLabel}</Button>
        {onCancel && <Button variant="secondary" onClick={onCancel} disabled={saving}>Cancel</Button>}
      </div>
    </form>
  )
}
