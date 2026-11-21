/**
 * Wire contract between the AI tutor widget and the backend. The backend implements it in a
 * later chunk; all AI calls go through these endpoints, never straight to the model provider.
 * Field names are snake_case here because the feature spec fixes `page_type`, `course_id` and
 * `assignment_id`; the rest of this API follows the same style.
 *
 * GET  /api/ai-tutor/config[?course_id=N]  ->  { "enabled": boolean }
 *   Whether the tutor is on for this student: global flag AND (when course_id is given) the
 *   per-course flag AND the caller is an enrolled student. Off by default. 401/403/404, or any
 *   failure, is treated as "off" by the widget.
 *
 * POST /api/ai-tutor/chat   (JSON body: TutorChatRequest, CSRF header as for every POST)
 *   200 text/event-stream. Each event is one `data: <json>` line followed by a blank line:
 *     data: {"type":"delta","text":"..."}                       streamed answer text
 *     data: {"type":"done"}                                     end of answer
 *     data: {"type":"error","message":"...","code":"..."}       failure after streaming began
 *   Non-200 answers carry JSON { "message": string, "retry_after_seconds"?: number }:
 *     401 not signed in, 403 not enrolled or not allowed to see the assignment,
 *     429 per-user rate limit (also send a Retry-After header), 503 AI provider unavailable.
 *   The backend must re-check enrollment and assignment visibility on every request and must
 *   never trust the ids below. `messages` is the whole conversation so far (the backend is
 *   stateless); local greetings and notices are never sent.
 */
export type TutorPageType = 'dashboard' | 'course' | 'assignment' | 'other'

export interface TutorPageContext {
  page_type: TutorPageType
  course_id: number | null
  assignment_id: number | null
}

export interface TutorChatMessage {
  role: 'user' | 'assistant'
  content: string
}

export interface TutorChatRequest {
  messages: TutorChatMessage[]
  context: TutorPageContext
  /** IANA zone such as "Europe/Oslo", so due dates can be shown in the student's timezone. */
  timezone: string
}

export interface TutorConfigResponse {
  enabled: boolean
}

export type TutorStreamEvent =
  | { type: 'delta'; text: string }
  | { type: 'done' }
  | { type: 'error'; message: string; code?: string }
