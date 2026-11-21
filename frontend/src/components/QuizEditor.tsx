import { useEffect, useState } from 'react'
import { quizzes, submissions } from '../services'
import { ApiError, ApiResponseError } from '../services/apiClient'
import Button from './ui/Button'
import ErrorMessage from './ui/ErrorMessage'
import LoadingIndicator from './ui/LoadingIndicator'
import QuizResults from './QuizResults'
import type { ContentResponse } from '../types/content'
import { BLANK } from '../types/quiz'
import type { QuestionRequest, QuestionType, QuestionWithAnswers } from '../types/quiz'

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
 * attempt is graded against the same answers. Attempts are graded automatically. Students see their
 * score and hints straight away unless the teacher turns that off, and then once the teacher
 * publishes the results. The attempt limit can be changed at any time.
 */
export default function QuizEditor({ quiz }: { quiz: ContentResponse }) {
  const [state, setState] = useState<EditorState>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)
  const [published, setPublished] = useState(quiz.published === true)
  const [maxAttempts, setMaxAttempts] = useState(quiz.maxAttempts ?? 0)
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
        <span>{attemptsLabel(maxAttempts)} per student.</span>
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
          Each question is worth 1 point and is right or wrong as a whole: every correct option
          selected and no wrong ones, the whole order right, every pair matched, or every blank
          filled in correctly. Partly correct answers give 0 points.
        </span>
        {!published && (
          <Button onClick={publish} loading={publishing} disabled={questions.length === 0}>
            Publish quiz
          </Button>
        )}
      </div>
      {actionError && <ErrorMessage message={actionError} />}
      <QuizSettingsForm quiz={quiz} maxAttempts={maxAttempts} onSaved={setMaxAttempts} />

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
                  {question.type === 'ORDERING' ? (
                    <>
                      <p className="quiz-type-label">Ordering question. Correct order (students see the options shuffled):</p>
                      <ol className="quiz-option-list quiz-order-answer">
                        {question.options.map(option => <li key={option.id}>{option.text}</li>)}
                      </ol>
                    </>
                  ) : question.type === 'MATCHING' ? (
                    <>
                      <p className="quiz-type-label">Matching question. Pairs (students see the right-hand side shuffled):</p>
                      <ul className="quiz-option-list quiz-pair-answer">
                        {question.options.map(option => (
                          <li key={option.id}>{option.text} <span aria-hidden="true">→</span> {option.matchText}</li>
                        ))}
                      </ul>
                    </>
                  ) : question.type === 'FILL_IN' ? (
                    <>
                      <p className="quiz-type-label">Fill-in question. Accepted answers per blank:</p>
                      <ol className="quiz-option-list quiz-blank-answer">
                        {question.options.map(option => (
                          <li key={option.id}>{option.text.split('|').map(answer => answer.trim()).join(' | ')}</li>
                        ))}
                      </ol>
                    </>
                  ) : (
                    <ul className="quiz-option-list">
                      {question.options.map(option => (
                        <li key={option.id} className={option.correct ? 'quiz-option-correct' : undefined}>
                          {option.text}
                          {option.correct && <span className="quiz-correct-badge"> ✓ Correct</span>}
                        </li>
                      ))}
                    </ul>
                  )}
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
  matchText: string
}

const emptyOption = (): OptionDraft => ({ text: '', correct: false, matchText: '' })
const emptyOptions = (): OptionDraft[] => [emptyOption(), emptyOption()]

const TYPE_LABELS: Record<QuestionType, string> = {
  CHOICE: 'Choice: students select the correct answer(s)',
  ORDERING: 'Ordering: students put the options in the right order',
  MATCHING: 'Matching: students match each item with its partner',
  FILL_IN: 'Fill in the blanks: students type the missing words',
}

const LEGENDS: Record<QuestionType, string> = {
  CHOICE: 'Answer options (tick the correct ones)',
  ORDERING: 'Options, in the correct order (students see them shuffled)',
  MATCHING: 'Pairs (students see the right-hand side shuffled)',
  FILL_IN: 'Blanks, in the order they appear in the question',
}

const blanksIn = (text: string) => text.split(BLANK).length - 1

/** Add/edit form for one question. What the option rows mean depends on the question type. */
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
  const [type, setType] = useState<QuestionType>(initial?.type ?? 'CHOICE')
  const [options, setOptions] = useState<OptionDraft[]>(
    initial
      ? initial.options.map(option => ({
        text: option.text, correct: option.correct, matchText: option.matchText ?? '',
      }))
      : emptyOptions())
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  function updateOption(index: number, change: Partial<OptionDraft>) {
    setOptions(previous => previous.map((option, i) => (i === index ? { ...option, ...change } : option)))
  }

  /** Checks the form for the chosen type and returns the request, or the message to show. */
  function build(): QuestionRequest | string {
    const trimmed = options.map(option => ({
      ...option, text: option.text.trim(), matchText: option.matchText.trim(),
    }))
    const question = text.trim()
    if (type === 'MATCHING') {
      const rows = trimmed.filter(option => option.text || option.matchText)
      if (rows.some(option => !option.text || !option.matchText)) {
        return 'Fill in both sides of every pair.'
      }
      if (rows.length < 2) return 'Add at least two pairs.'
      return {
        text: question, hint: hint.trim() || null, type,
        options: rows.map(option => ({ text: option.text, correct: false, matchText: option.matchText })),
      }
    }
    const filled = trimmed.filter(option => option.text)
    if (type === 'FILL_IN') {
      if (filled.length === 0) return 'Add the accepted answer for each blank.'
      const blanks = blanksIn(question)
      if (blanks !== filled.length) {
        return `The question has ${blanks} blank${blanks === 1 ? '' : 's'} (${BLANK}) but you gave `
          + `${filled.length} answer${filled.length === 1 ? '' : 's'}. Use one ${BLANK} per blank.`
      }
      return {
        text: question, hint: hint.trim() || null, type,
        options: filled.map(option => ({ text: option.text, correct: false })),
      }
    }
    if (filled.length < 2) return 'Add at least two answer options.'
    if (type === 'CHOICE' && !filled.some(option => option.correct)) {
      return 'Mark at least one option as correct.'
    }
    return {
      text: question, hint: hint.trim() || null, type,
      options: filled.map(option => ({ text: option.text, correct: option.correct })),
    }
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    const request = build()
    if (typeof request === 'string') {
      setError(request)
      return
    }
    setSaving(true)
    setError(null)
    try {
      await onSave(request)
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
        {type === 'FILL_IN' && (
          <p className="ui-hint">
            Type {BLANK} (three underscores) in the question wherever a word is missing, for example
            "The capital of {BLANK} is {BLANK}."
          </p>
        )}
      </div>
      <fieldset className="quiz-type-fields">
        <legend className="ui-label">Question type</legend>
        {(Object.keys(TYPE_LABELS) as QuestionType[]).map(candidate => (
          <label key={candidate} className="ui-checkbox-field">
            <input
              type="radio"
              name={`${idPrefix}-type`}
              checked={type === candidate}
              onChange={() => setType(candidate)}
            />
            {TYPE_LABELS[candidate]}
          </label>
        ))}
      </fieldset>
      <fieldset className="quiz-option-fields">
        <legend className="ui-label">{LEGENDS[type]}</legend>
        {options.map((option, index) => (
          <div key={index} className="quiz-option-field">
            {type === 'CHOICE' ? (
              <input
                type="checkbox"
                aria-label={`Option ${index + 1} is correct`}
                checked={option.correct}
                onChange={event => updateOption(index, { correct: event.target.checked })}
              />
            ) : (
              <span className="quiz-option-number" aria-hidden="true">{index + 1}.</span>
            )}
            {type === 'MATCHING' ? (
              <>
                <input
                  className="ui-input"
                  aria-label={`Pair ${index + 1} left`}
                  value={option.text}
                  onChange={event => updateOption(index, { text: event.target.value })}
                />
                <span aria-hidden="true">→</span>
                <input
                  className="ui-input"
                  aria-label={`Pair ${index + 1} right`}
                  value={option.matchText}
                  onChange={event => updateOption(index, { matchText: event.target.value })}
                />
              </>
            ) : (
              <input
                className="ui-input"
                aria-label={type === 'FILL_IN' ? `Blank ${index + 1} accepted answers` : `Option ${index + 1}`}
                value={option.text}
                onChange={event => updateOption(index, { text: event.target.value })}
              />
            )}
            {options.length > (type === 'FILL_IN' ? 1 : 2) && (
              <Button
                variant="secondary"
                onClick={() => setOptions(previous => previous.filter((_, i) => i !== index))}
              >
                Remove
              </Button>
            )}
          </div>
        ))}
        {type === 'FILL_IN' && (
          <p className="ui-hint">
            Separate answers that are also accepted with | , for example "Norway | Norge". Capital
            letters and extra spaces do not matter.
          </p>
        )}
        <Button variant="secondary" onClick={() => setOptions(previous => [...previous, emptyOption()])}>
          {type === 'MATCHING' ? 'Add pair' : type === 'FILL_IN' ? 'Add blank' : 'Add option'}
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
        <p className="ui-hint">Shown to students who answer this question wrong, together with their results.</p>
      </div>
      {error && <ErrorMessage message={error} />}
      <div className="page-heading-actions">
        <Button type="submit" loading={saving}>{submitLabel}</Button>
        {onCancel && <Button variant="secondary" onClick={onCancel} disabled={saving}>Cancel</Button>}
      </div>
    </form>
  )
}

/**
 * The attempt limit, which can be changed at any time, even after the quiz is published. The
 * deadline is changed through the submission policy and result visibility in the status box.
 */
function QuizSettingsForm({ quiz, maxAttempts: current, onSaved }: {
  quiz: ContentResponse
  maxAttempts: number
  onSaved: (maxAttempts: number) => void
}) {
  const [maxAttempts, setMaxAttempts] = useState(String(current))
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setSaved(false)
    if (!/^\d+$/.test(maxAttempts.trim())) {
      setError('Maximum attempts must be a whole number, 0 or more (0 means unlimited).')
      return
    }
    const attempts = Number(maxAttempts.trim())
    setSaving(true)
    setError(null)
    try {
      await quizzes.updateSettings(quiz.id, { maxAttempts: attempts }, {})
      onSaved(attempts)
      setSaved(true)
    } catch (saveError) {
      setError(errorMessage(saveError))
    } finally {
      setSaving(false)
    }
  }

  return (
    <form className="quiz-settings" onSubmit={handleSubmit} aria-label="Quiz settings">
      <h3>Settings</h3>
      <div className="ui-field">
        <label className="ui-label" htmlFor="quiz-max-attempts">Maximum attempts</label>
        <input
          id="quiz-max-attempts"
          className="ui-input"
          type="number"
          min={0}
          value={maxAttempts}
          onChange={event => { setMaxAttempts(event.target.value); setSaved(false) }}
        />
        <p className="ui-hint">0 means unlimited. The deadline is set above.</p>
      </div>
      {error && <ErrorMessage message={error} />}
      {saved && <p role="status" className="quiz-settings-saved">Settings saved.</p>}
      <Button type="submit" loading={saving}>Save settings</Button>
    </form>
  )
}
