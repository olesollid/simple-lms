/**
 * Wire contract between the AI tutor widget and the backend. The backend implements it in a
 * later chunk; all AI calls go through these endpoints, never straight to the model provider.
 * Field names are snake_case here because the feature spec fixes `page_type` and `course_id`; the
 * rest of this API follows the same style. The tutor only knows whether the student is inside a
 * course (and which one): no assignment, module or content id is ever sent.
 *
 * GET  /api/ai-tutor/config[?course_id=N]  ->  { "enabled": boolean }
 *   Whether the tutor is on for this student: the global flag AND the caller is an enrolled
 *   student (of that course, or of any course when course_id is left out). Off by default.
 *   401/403/404, or any failure, is treated as "off" by the widget.
 *
 * POST /api/ai-tutor/chat   (JSON body: TutorChatRequest, CSRF header as for every POST)
 *   200 text/event-stream. Each event is one `data: <json>` line followed by a blank line:
 *     data: {"type":"delta","text":"..."}                       streamed answer text
 *     data: {"type":"done"}                                     end of answer
 *     data: {"type":"error","message":"...","code":"..."}       failure after streaming began
 *   Requests the server refuses before streaming starts get a normal JSON error
 *   { "message": string, "retry_after_seconds"?: number }, so the client must send
 *   `Accept: text/event-stream, application/json`:
 *     400 malformed request, 401 not signed in, 403 tutor off or not enrolled, 429 per-user
 *     rate limit (with a Retry-After header), 503 tutor not configured or too busy.
 *   A failure of the AI provider itself arrives as an `error` event on the stream
 *   (code "provider_unavailable"), because the 200 response has already started.
 *   The backend must re-check enrollment on every request and must
 *   never trust the course id below. `messages` is the whole conversation so far (the backend is
 *   stateless); local greetings and notices are never sent.
 */
export type TutorPageType = 'dashboard' | 'course' | 'other'

export interface TutorPageContext {
  page_type: TutorPageType
  course_id: number | null
}

export interface TutorChatMessage {
  role: 'user' | 'assistant'
  content: string
}

export interface TutorChatRequest {
  messages: TutorChatMessage[]
  context: TutorPageContext
}

export interface TutorConfigResponse {
  enabled: boolean
}

export type TutorStreamEvent =
  | { type: 'delta'; text: string }
  | { type: 'done' }
  | { type: 'error'; message: string; code?: string }
