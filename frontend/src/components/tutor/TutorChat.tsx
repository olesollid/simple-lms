import { useEffect, useId, useRef, useState } from 'react'
import Button from '../ui/Button'
import ErrorMessage from '../ui/ErrorMessage'
import TutorMarkdown from './TutorMarkdown'
import { MAX_MESSAGE_LENGTH, useTutorChat } from './useTutorChat'
import { useMediaQuery } from './useMediaQuery'
import { useTutorPageContext } from './useTutorPageContext'
import type { TutorRoute } from './tutorRoute'
import type { ChatEntry } from './tutorTypes'
import './tutor.css'

const FOCUSABLE = 'a[href], button:not([disabled]), textarea:not([disabled]), input:not([disabled]), [tabindex]:not([tabindex="-1"])'

function EntryView({ entry, onRetry }: { entry: ChatEntry; onRetry: () => void }) {
  if (entry.kind === 'notice') {
    return <p className="tutor-notice">{entry.content}</p>
  }
  if (entry.kind === 'user') {
    return (
      <div className="tutor-msg tutor-msg--user">
        <span className="tutor-sr-only">You: </span>
        <p>{entry.content}</p>
      </div>
    )
  }
  return (
    <div className="tutor-msg tutor-msg--assistant">
      <span className="tutor-sr-only">AI tutor: </span>
      {entry.status === 'streaming' && !entry.content ? (
        <div className="tutor-typing" role="status">
          <span className="tutor-typing-dots" aria-hidden="true"><span /><span /><span /></span>
          <span className="tutor-sr-only">AI tutor is typing</span>
        </div>
      ) : (
        entry.content && <TutorMarkdown>{entry.content}</TutorMarkdown>
      )}
      {entry.status === 'error' && (
        <ErrorMessage message={entry.error ?? 'Something went wrong.'}>
          <Button variant="secondary" onClick={onRetry}>Retry</Button>
        </ErrorMessage>
      )}
    </div>
  )
}

export default function TutorChat({ email, route }: { email: string; route: TutorRoute }) {
  const { context, names, retry: retryNames } = useTutorPageContext(route)
  const chat = useTutorChat({ email, context, names })
  const isNarrow = useMediaQuery('(max-width: 767px)')
  const [draft, setDraft] = useState('')
  const titleId = useId()
  const fabRef = useRef<HTMLButtonElement>(null)
  const panelRef = useRef<HTMLElement>(null)
  const inputRef = useRef<HTMLTextAreaElement>(null)
  const logRef = useRef<HTMLDivElement>(null)
  // Focus only moves when the student opens or closes the panel, never when a stored
  // "open" state is restored on page load.
  const focusInput = useRef(false)
  const focusButton = useRef(false)

  const { open, setOpen } = chat

  useEffect(() => {
    if (open && focusInput.current) {
      focusInput.current = false
      inputRef.current?.focus()
    }
    if (!open && focusButton.current) {
      focusButton.current = false
      fabRef.current?.focus()
    }
  }, [open])

  useEffect(() => {
    const log = logRef.current
    if (log) log.scrollTop = log.scrollHeight
  }, [chat.entries, open])

  function openPanel() {
    focusInput.current = true
    setOpen(true)
  }

  function closePanel() {
    focusButton.current = true
    setOpen(false)
  }

  function handleKeyDown(event: React.KeyboardEvent) {
    if (event.key === 'Escape') {
      event.stopPropagation()
      closePanel()
      return
    }
    // The full-screen panel covers the page, so keep Tab inside it like a dialog.
    if (event.key === 'Tab' && isNarrow && panelRef.current) {
      const items = [...panelRef.current.querySelectorAll<HTMLElement>(FOCUSABLE)]
      if (items.length === 0) return
      const first = items[0]
      const last = items[items.length - 1]
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first.focus()
      }
    }
  }

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    if (!draft.trim() || chat.streaming) return
    chat.send(draft)
    setDraft('')
  }

  const lastEntry = chat.entries.at(-1)

  return (
    <div className="tutor-root">
      <button
        ref={fabRef}
        type="button"
        className="tutor-fab"
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-controls="tutor-panel"
        onClick={openPanel}
        hidden={open}
      >
        <span aria-hidden="true">✦</span> AI tutor
      </button>

      {open && (
        <section
          id="tutor-panel"
          ref={panelRef}
          className="tutor-panel"
          role="dialog"
          aria-modal={isNarrow}
          aria-labelledby={titleId}
          onKeyDown={handleKeyDown}
        >
          <header className="tutor-header">
            <h2 id={titleId} className="tutor-title">AI tutor</h2>
            <Button variant="secondary" className="tutor-small" onClick={chat.newConversation}>
              New conversation
            </Button>
            <Button variant="secondary" className="tutor-small" onClick={closePanel} aria-label="Close AI tutor">
              <span aria-hidden="true">✕</span>
            </Button>
          </header>

          <p className="tutor-disclosure">
            You're chatting with an AI, not a person. It can make mistakes, so check important
            details against your course material.
          </p>

          {names.status === 'error' && (
            <ErrorMessage message="I couldn't load details about this page, so I'm using general context.">
              <Button variant="secondary" onClick={retryNames}>Retry</Button>
            </ErrorMessage>
          )}

          <div
            ref={logRef}
            className="tutor-log"
            role="log"
            aria-label="Conversation"
            aria-live="polite"
            aria-relevant="additions text"
            aria-busy={chat.streaming}
            tabIndex={0}
          >
            {chat.entries.map(entry => (
              <EntryView
                key={entry.id}
                entry={entry}
                onRetry={entry === lastEntry ? chat.retry : () => undefined}
              />
            ))}
          </div>

          <form className="tutor-form" onSubmit={handleSubmit}>
            <label className="tutor-sr-only" htmlFor="tutor-input">Message to the AI tutor</label>
            <textarea
              id="tutor-input"
              ref={inputRef}
              className="ui-input tutor-input"
              rows={2}
              maxLength={MAX_MESSAGE_LENGTH}
              placeholder="Ask a question…"
              value={draft}
              onChange={event => setDraft(event.target.value)}
              onKeyDown={event => {
                if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
                  event.preventDefault()
                  event.currentTarget.form?.requestSubmit()
                }
              }}
            />
            <Button type="submit" disabled={!draft.trim() || chat.streaming}>Send</Button>
          </form>
        </section>
      )}
    </div>
  )
}
