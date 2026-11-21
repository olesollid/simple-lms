import type { TutorChatRequest, TutorConfigResponse, TutorStreamEvent } from '../types/aiTutor.ts'
import { readXsrfToken } from './apiClient.ts'
import type { ApiCallOptions, ApiRequest } from './apiClient.ts'

/** The tutor endpoint refused the request, or the stream failed. */
export class TutorStreamError extends Error {
  readonly status: number | null
  readonly code: string | null
  readonly retryAfterSeconds: number | null

  constructor(message: string, status: number | null = null, code: string | null = null,
    retryAfterSeconds: number | null = null) {
    super(message)
    this.name = 'TutorStreamError'
    this.status = status
    this.code = code
    this.retryAfterSeconds = retryAfterSeconds
  }
}

function parseBlock(block: string): TutorStreamEvent | null {
  const data = block.split(/\r?\n/)
    .filter(line => line.startsWith('data:'))
    .map(line => line.slice(5).replace(/^ /, ''))
    .join('\n')
  if (!data) return null
  let value: unknown
  try {
    value = JSON.parse(data)
  } catch {
    return null
  }
  if (typeof value !== 'object' || value === null || !('type' in value)) return null
  if (value.type === 'delta' && 'text' in value && typeof value.text === 'string') {
    return { type: 'delta', text: value.text }
  }
  if (value.type === 'done') return { type: 'done' }
  if (value.type === 'error' && 'message' in value && typeof value.message === 'string') {
    const code = 'code' in value && typeof value.code === 'string' ? value.code : undefined
    return { type: 'error', message: value.message, code }
  }
  return null
}

/** Turns a Server-Sent-Events byte stream into events. Unknown or malformed blocks are skipped. */
export async function* parseSseStream(body: ReadableStream<Uint8Array>): AsyncGenerator<TutorStreamEvent> {
  const reader = body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      let boundary = /\r?\n\r?\n/.exec(buffer)
      while (boundary) {
        const event = parseBlock(buffer.slice(0, boundary.index))
        buffer = buffer.slice(boundary.index + boundary[0].length)
        if (event) yield event
        boundary = /\r?\n\r?\n/.exec(buffer)
      }
    }
    buffer += decoder.decode()
    const last = buffer.trim() ? parseBlock(buffer) : null
    if (last) yield last
  } finally {
    // Stops the download if the consumer quit early (done event, abort or error).
    reader.cancel().catch(() => undefined)
  }
}

async function toStreamError(response: Response): Promise<TutorStreamError> {
  let message = `The AI tutor is unavailable (${response.status}).`
  let retryAfter: number | null = null
  const header = Number(response.headers.get('Retry-After'))
  if (Number.isFinite(header) && header > 0) retryAfter = header
  try {
    const data: unknown = JSON.parse(await response.text())
    if (typeof data === 'object' && data !== null) {
      if ('message' in data && typeof data.message === 'string' && data.message.trim()) {
        message = data.message
      }
      if ('retry_after_seconds' in data && typeof data.retry_after_seconds === 'number') {
        retryAfter = data.retry_after_seconds
      }
    }
  } catch {
    // Keep the generic message; the status code is what the UI relies on.
  }
  return new TutorStreamError(message, response.status, null, retryAfter)
}

export function createAiTutorService(request: ApiRequest, transport: {
  baseUrl: string
  fetchImplementation?: typeof fetch
}) {
  const base = transport.baseUrl.trim().replace(/\/+$/, '')
  const fetchImplementation = transport.fetchImplementation ?? fetch.bind(globalThis)

  return {
    getConfig: (courseId: number | null, options?: ApiCallOptions) =>
      request<TutorConfigResponse>(
        `/ai-tutor/config${courseId == null ? '' : `?course_id=${courseId}`}`, options),

    /** Streams the answer. The shared request client reads whole bodies, so this uses fetch directly. */
    async* streamChat(body: TutorChatRequest, options?: { signal?: AbortSignal }): AsyncGenerator<TutorStreamEvent> {
      const headers = new Headers({ Accept: 'text/event-stream', 'Content-Type': 'application/json' })
      const token = readXsrfToken()
      if (token) headers.set('X-XSRF-TOKEN', token)
      const response = await fetchImplementation(`${base}/ai-tutor/chat`, {
        method: 'POST',
        headers,
        credentials: 'include',
        body: JSON.stringify(body),
        signal: options?.signal,
      })
      if (!response.ok) throw await toStreamError(response)
      if (!response.body) throw new TutorStreamError('The AI tutor returned no data.', response.status)
      yield* parseSseStream(response.body)
    },
  }
}
