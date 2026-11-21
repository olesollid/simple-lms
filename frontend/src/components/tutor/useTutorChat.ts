import { useEffect, useRef, useState } from 'react'
import { aiTutor } from '../../services'
import { TutorStreamError } from '../../services/aiTutor'
import type { TutorChatMessage, TutorPageContext } from '../../types/aiTutor'
import { greetingText, noticeText } from './tutorGreeting'
import { loadTutorState, saveTutorState } from './tutorStorage'
import type { ChatEntry, TutorNames } from './tutorTypes'

export const MAX_MESSAGE_LENGTH = 2000

function newId(): string {
  try {
    return crypto.randomUUID()
  } catch {
    return `${Date.now()}-${Math.random().toString(36).slice(2)}`
  }
}

function isConversation(entry: ChatEntry): boolean {
  return entry.kind === 'user' || entry.kind === 'assistant'
}

export function tutorErrorMessage(error: unknown): string {
  if (error instanceof TutorStreamError) {
    if (error.status === 429) {
      const wait = error.retryAfterSeconds
      return wait
        ? `You've sent too many messages. Please wait about ${Math.ceil(wait)} seconds and try again.`
        : "You've sent too many messages. Please wait a moment and try again."
    }
    if (error.status === 401) return 'Your session has ended. Sign in again to keep chatting.'
    if (error.status === 403) return "The AI tutor isn't available for this course."
    return 'The AI tutor is unavailable right now. Try again in a moment.'
  }
  return 'Could not reach the AI tutor. Check your connection and try again.'
}

/** Conversation, open/closed state and streaming. Everything survives navigation and tab reloads. */
export function useTutorChat({ email, context, names }: {
  email: string
  context: TutorPageContext
  names: TutorNames
}) {
  const [stored] = useState(() => loadTutorState(email))
  const [open, setOpen] = useState(stored?.open ?? false)
  const [entries, setEntries] = useState<ChatEntry[]>(stored?.entries ?? [])
  const [noticedCourseId, setNoticedCourseId] = useState<number | null>(stored?.noticedCourseId ?? null)
  const [streaming, setStreaming] = useState(false)
  const abortRef = useRef<AbortController | null>(null)

  const hasConversation = entries.some(isConversation)

  // Switching to another course mid-conversation adds a notice; later messages carry the new
  // context. Adjusting state during render (rather than in an effect) avoids a flash of stale UI.
  if (names.status !== 'loading' && context.course_id != null && noticedCourseId !== context.course_id) {
    setNoticedCourseId(context.course_id)
    if (noticedCourseId != null && hasConversation) {
      setEntries(previous => [...previous, {
        id: `notice-${previous.length}-${context.course_id}`,
        kind: 'notice',
        content: noticeText(names),
      }])
    }
  }

  const greeting: ChatEntry | null = names.status === 'loading'
    ? null
    : { id: 'greeting', kind: 'greeting', content: greetingText(context, names) }
  const displayEntries = hasConversation ? entries : greeting ? [greeting] : []

  useEffect(() => {
    if (streaming) return
    saveTutorState(email, { open, entries, noticedCourseId })
  }, [email, open, entries, noticedCourseId, streaming])

  useEffect(() => () => abortRef.current?.abort(), [])

  function updateEntry(id: string, change: (entry: ChatEntry) => ChatEntry) {
    setEntries(previous => previous.map(entry => entry.id === id ? change(entry) : entry))
  }

  async function run(history: ChatEntry[], assistantId: string) {
    const controller = new AbortController()
    abortRef.current = controller
    setStreaming(true)
    // Local greetings/notices and failed or empty answers are never sent to the model.
    const messages: TutorChatMessage[] = history.slice(0, -1)
      .filter(entry => isConversation(entry) && entry.status !== 'error' && entry.content.trim())
      .map(entry => ({ role: entry.kind as 'user' | 'assistant', content: entry.content }))
    try {
      let received = false
      for await (const event of aiTutor.streamChat({
        messages,
        context,
        timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
      }, { signal: controller.signal })) {
        if (event.type === 'delta') {
          received = true
          updateEntry(assistantId, entry => ({ ...entry, content: entry.content + event.text }))
        } else if (event.type === 'error') {
          throw new TutorStreamError(event.message, null, event.code ?? null)
        } else {
          break
        }
      }
      if (!received) throw new TutorStreamError('The AI tutor returned an empty answer.')
      updateEntry(assistantId, entry => ({ ...entry, status: 'done' }))
    } catch (error) {
      if (controller.signal.aborted) return
      updateEntry(assistantId, entry => ({ ...entry, status: 'error', error: tutorErrorMessage(error) }))
    } finally {
      if (abortRef.current === controller) {
        abortRef.current = null
        setStreaming(false)
      }
    }
  }

  function send(text: string) {
    const content = text.trim().slice(0, MAX_MESSAGE_LENGTH)
    if (!content || streaming) return
    const userEntry: ChatEntry = { id: newId(), kind: 'user', content, status: 'done' }
    const assistantEntry: ChatEntry = { id: newId(), kind: 'assistant', content: '', status: 'streaming' }
    const base = hasConversation ? entries : greeting ? [greeting] : []
    const next = [...base, userEntry, assistantEntry]
    setEntries(next)
    void run(next, assistantEntry.id)
  }

  function retry() {
    const last = entries.at(-1)
    if (streaming || !last || last.kind !== 'assistant' || last.status !== 'error') return
    const assistantEntry: ChatEntry = { id: newId(), kind: 'assistant', content: '', status: 'streaming' }
    const next = [...entries.slice(0, -1), assistantEntry]
    setEntries(next)
    void run(next, assistantEntry.id)
  }

  function newConversation() {
    abortRef.current?.abort()
    abortRef.current = null
    setStreaming(false)
    setEntries([])
  }

  return {
    open,
    setOpen,
    entries: displayEntries,
    streaming,
    send,
    retry,
    newConversation,
  }
}
