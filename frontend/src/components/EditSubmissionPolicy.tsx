import { useState } from 'react'
import { contents } from '../services'
import { contentDetailError } from '../pages/contentDetailState'
import type { ContentResponse } from '../types/content'
import Button from './ui/Button'
import ErrorMessage from './ui/ErrorMessage'
import SubmissionPolicyFields from './SubmissionPolicyFields'

export default function EditSubmissionPolicy({ content, onSaved }: { content: ContentResponse; onSaved: () => void }) {
  const [editing, setEditing] = useState(false)
  const [deadline, setDeadline] = useState(content.deadline ?? '')
  const [acceptsLate, setAcceptsLate] = useState(content.lateSubmissionsAccepted ?? false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const isQuiz = content.type === 'Quiz'

  async function save(event: React.FormEvent) {
    event.preventDefault()
    setSaving(true)
    setError(null)
    try {
      await contents.updateSubmissionPolicy(content.id, { deadline: deadline || null, lateSubmissionsAccepted: acceptsLate })
      setEditing(false)
      onSaved()
    } catch (error) {
      setError(contentDetailError(error))
    } finally {
      setSaving(false)
    }
  }

  if (!editing) return <Button variant="secondary" onClick={() => {
    setDeadline(content.deadline ?? '')
    setAcceptsLate(content.lateSubmissionsAccepted ?? false)
    setError(null)
    setEditing(true)
  }}>{isQuiz ? 'Edit deadline' : 'Edit deadline and late policy'}</Button>

  return <form onSubmit={save} aria-label="Submission deadline settings">
    <fieldset disabled={saving} style={{ border: 0, padding: 0 }}>
      <SubmissionPolicyFields deadline={deadline} acceptsLate={acceptsLate}
        onDeadline={setDeadline} onAcceptsLate={isQuiz ? undefined : setAcceptsLate} />
      {error && <ErrorMessage message={error} />}
      <div className="page-heading-actions">
        <Button type="submit" loading={saving}>Save deadline settings</Button>
        <Button variant="secondary" onClick={() => setEditing(false)}>Cancel</Button>
      </div>
    </fieldset>
  </form>
}
