import { useState } from 'react'
import { contents } from '../services'
import { ApiError, ApiResponseError } from '../services/apiClient'
import Button from './ui/Button'
import ErrorMessage from './ui/ErrorMessage'
import Input from './ui/Input'
import MarkdownEditor from './MarkdownEditor'
import type { ContentResponse } from '../types/content'

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message
  if (error instanceof ApiResponseError) return 'The server returned an unexpected response.'
  return 'Check your connection and try again.'
}

/** The editable main text per content type; quizzes are edited question by question instead. */
function textOf(content: ContentResponse): { label: string; value: string; multiline: boolean } | null {
  switch (content.type) {
    case 'StaticPage': return { label: 'Body', value: content.body ?? '', multiline: true }
    case 'Assignment': return { label: 'Instructions', value: content.instructions ?? '', multiline: true }
    case 'CodingExercise': return { label: 'Repository URL', value: content.repositoryUrl ?? '', multiline: false }
    default: return null
  }
}

/** `2026-12-01T08:00:00` → `2026-12-01T08:00`, the format a datetime-local input expects. */
const toInput = (value: string | null) => (value ? value.slice(0, 16) : '')

/**
 * Teacher form for a content item's title, main text and visibility window. Times are
 * Europe/Oslo wall-clock times, like deadlines.
 */
export default function EditContentForm({ content, onSaved, onCancel }: {
  content: ContentResponse
  onSaved: () => void
  onCancel: () => void
}) {
  const textField = textOf(content)
  const [title, setTitle] = useState(content.title)
  const [text, setText] = useState(textField?.value ?? '')
  const [visibleFrom, setVisibleFrom] = useState(toInput(content.visibleFrom))
  const [visibleUntil, setVisibleUntil] = useState(toInput(content.visibleUntil))
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    if (visibleFrom && visibleUntil && visibleUntil <= visibleFrom) {
      setError('"Visible until" must be after "Visible from".')
      return
    }
    setSaving(true)
    setError(null)
    try {
      await contents.updateContent(content.id, {
        title: title.trim(),
        position: content.position,
        visibleFrom: visibleFrom || null,
        visibleUntil: visibleUntil || null,
      }, {})
      if (textField && text !== textField.value) {
        await contents.updateText(content.id, text, {})
      }
      onSaved()
    } catch (saveError) {
      setError(errorMessage(saveError))
    } finally {
      setSaving(false)
    }
  }

  return (
    <form className="content-edit-form" onSubmit={handleSubmit} aria-label="Edit content">
      <h2>Edit content</h2>
      <Input label="Title" value={title} onChange={event => setTitle(event.target.value)} required />
      {textField && (textField.multiline ? (
        <div className="ui-field">
          <label className="ui-label" htmlFor="edit-content-text">
            {textField.label}<span className="ui-required" aria-hidden="true"> *</span>
          </label>
          {content.type === 'StaticPage'
            ? <MarkdownEditor id="edit-content-text" value={text} onChange={setText} rows={8} required />
            : (
              <textarea
                id="edit-content-text"
                className="ui-input"
                rows={8}
                value={text}
                onChange={event => setText(event.target.value)}
                required
              />
            )}
        </div>
      ) : (
        <Input label={textField.label} value={text} onChange={event => setText(event.target.value)} required />
      ))}
      <Input label="Visible from (Europe/Oslo)" type="datetime-local" value={visibleFrom}
        onChange={event => setVisibleFrom(event.target.value)} />
      <Input label="Visible until (Europe/Oslo)" type="datetime-local" value={visibleUntil}
        onChange={event => setVisibleUntil(event.target.value)} />
      <p className="ui-hint">Leave both empty to keep the content visible to students at all times.</p>
      {error && <ErrorMessage message={error} />}
      <div className="page-heading-actions">
        <Button type="submit" loading={saving}>Save changes</Button>
        <Button variant="secondary" onClick={onCancel} disabled={saving}>Cancel</Button>
      </div>
    </form>
  )
}
