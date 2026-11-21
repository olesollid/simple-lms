import type { ChatEntry } from './tutorTypes'

// Conversation state lives in sessionStorage: it survives navigation and reloads of this tab only,
// and is keyed by user so a second account in the same tab never sees the first one's chat.
// Nothing secret is stored here; the model provider key exists only on the server.
const PREFIX = 'ai-tutor:v1:'
const MAX_ENTRIES = 100

export interface StoredTutor {
  open: boolean
  entries: ChatEntry[]
  noticedCourseId: number | null
}

function isEntry(value: unknown): value is ChatEntry {
  if (typeof value !== 'object' || value === null) return false
  const entry = value as Record<string, unknown>
  return typeof entry.id === 'string' && typeof entry.content === 'string'
    && (entry.kind === 'user' || entry.kind === 'assistant'
      || entry.kind === 'greeting' || entry.kind === 'notice')
}

export function loadTutorState(email: string): StoredTutor | null {
  try {
    const raw = sessionStorage.getItem(PREFIX + email)
    if (!raw) return null
    const data: unknown = JSON.parse(raw)
    if (typeof data !== 'object' || data === null) return null
    const stored = data as Record<string, unknown>
    const entries = Array.isArray(stored.entries) ? stored.entries.filter(isEntry) : []
    return {
      open: stored.open === true,
      // A reply that was mid-stream when the page went away can never finish.
      entries: entries.map(entry => entry.status === 'streaming'
        ? { ...entry, status: 'error', error: 'This answer was interrupted.' }
        : entry),
      noticedCourseId: typeof stored.noticedCourseId === 'number' ? stored.noticedCourseId : null,
    }
  } catch {
    return null
  }
}

export function saveTutorState(email: string, state: StoredTutor): void {
  try {
    sessionStorage.setItem(PREFIX + email, JSON.stringify({
      ...state,
      entries: state.entries.slice(-MAX_ENTRIES),
    }))
  } catch {
    // Storage can be full or blocked; the chat keeps working in memory.
  }
}

/** Removes every stored conversation, for example when the user signs out. */
export function clearAllTutorStorage(): void {
  try {
    for (const key of Object.keys(sessionStorage)) {
      if (key.startsWith(PREFIX)) sessionStorage.removeItem(key)
    }
  } catch {
    // Nothing to clear if storage is unavailable.
  }
}
