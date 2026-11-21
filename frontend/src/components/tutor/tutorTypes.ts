export type EntryKind = 'user' | 'assistant' | 'greeting' | 'notice'

/** One item in the panel. Only user and assistant entries are ever sent to the backend. */
export interface ChatEntry {
  id: string
  kind: EntryKind
  content: string
  status?: 'streaming' | 'done' | 'error'
  /** Shown with a Retry button when status is 'error'. */
  error?: string
}

export interface TutorNames {
  status: 'loading' | 'ready' | 'error'
  courseName: string | null
}
