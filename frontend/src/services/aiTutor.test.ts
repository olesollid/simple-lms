import { afterEach, describe, expect, it, vi } from 'vitest'
import { createAiTutorService, parseSseStream, TutorStreamError } from './aiTutor'
import type { TutorChatRequest, TutorStreamEvent } from '../types/aiTutor'

const encoder = new TextEncoder()

function streamOf(...chunks: string[]): ReadableStream<Uint8Array> {
  return new ReadableStream({
    start(controller) {
      for (const chunk of chunks) controller.enqueue(encoder.encode(chunk))
      controller.close()
    },
  })
}

async function collect(stream: AsyncGenerator<TutorStreamEvent>): Promise<TutorStreamEvent[]> {
  const events: TutorStreamEvent[] = []
  for await (const event of stream) events.push(event)
  return events
}

const body: TutorChatRequest = {
  messages: [{ role: 'user', content: 'Hi' }],
  context: { page_type: 'course', course_id: 1, assignment_id: null },
  timezone: 'Europe/Oslo',
}

describe('parseSseStream', () => {
  it('parses events even when they are split across chunks', async () => {
    const events = await collect(parseSseStream(streamOf(
      'data: {"type":"delta","te', 'xt":"Hel"}\n\ndata: {"type":"delta","text":"lo"}\n',
      '\ndata: {"type":"done"}\n\n',
    )))

    expect(events).toEqual([
      { type: 'delta', text: 'Hel' },
      { type: 'delta', text: 'lo' },
      { type: 'done' },
    ])
  })

  it('handles CRLF line endings and a final event without a trailing blank line', async () => {
    const events = await collect(parseSseStream(streamOf(
      'data: {"type":"delta","text":"a"}\r\n\r\ndata: {"type":"done"}',
    )))

    expect(events).toEqual([{ type: 'delta', text: 'a' }, { type: 'done' }])
  })

  it('skips comments, malformed JSON and unknown event types', async () => {
    const events = await collect(parseSseStream(streamOf(
      ': keep-alive\n\n',
      'data: not json\n\n',
      'data: {"type":"mystery"}\n\n',
      'data: {"type":"delta","text":42}\n\n',
      'data: {"type":"error","message":"Model failed","code":"provider"}\n\n',
    )))

    expect(events).toEqual([{ type: 'error', message: 'Model failed', code: 'provider' }])
  })
})

describe('createAiTutorService', () => {
  afterEach(() => {
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
  })

  it('asks for the config with and without a course id', async () => {
    const request = vi.fn().mockResolvedValue({ enabled: true })
    const service = createAiTutorService(request, { baseUrl: '/api' })

    await service.getConfig(3)
    await service.getConfig(null)

    expect(request).toHaveBeenNthCalledWith(1, '/ai-tutor/config?course_id=3', undefined)
    expect(request).toHaveBeenNthCalledWith(2, '/ai-tutor/config', undefined)
  })

  it('posts the conversation with credentials and the CSRF token, and streams the answer', async () => {
    document.cookie = 'XSRF-TOKEN=token-123'
    const fetchMock = vi.fn().mockResolvedValue(new Response(
      streamOf('data: {"type":"delta","text":"Hi"}\n\ndata: {"type":"done"}\n\n'),
      { status: 200, headers: { 'Content-Type': 'text/event-stream' } },
    ))
    const service = createAiTutorService(vi.fn(), { baseUrl: '/api/', fetchImplementation: fetchMock })

    const events = await collect(service.streamChat(body))

    expect(events).toEqual([{ type: 'delta', text: 'Hi' }, { type: 'done' }])
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/ai-tutor/chat')
    expect(init.method).toBe('POST')
    expect(init.credentials).toBe('include')
    expect(init.headers.get('Accept')).toBe('text/event-stream, application/json')
    expect(init.headers.get('X-XSRF-TOKEN')).toBe('token-123')
    expect(JSON.parse(init.body)).toEqual(body)
  })

  it('turns a 429 into an error carrying the wait time', async () => {
    const fetchMock = vi.fn().mockResolvedValue(Response.json(
      { message: 'Too many requests', retry_after_seconds: 30 },
      { status: 429 },
    ))
    const service = createAiTutorService(vi.fn(), { baseUrl: '/api', fetchImplementation: fetchMock })

    const failure = collect(service.streamChat(body))

    await expect(failure).rejects.toBeInstanceOf(TutorStreamError)
    await expect(failure).rejects.toMatchObject({ status: 429, retryAfterSeconds: 30, message: 'Too many requests' })
  })

  it('reads Retry-After from the header when the body has none', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response('slow down', { status: 429, headers: { 'Retry-After': '12' } }),
    )
    const service = createAiTutorService(vi.fn(), { baseUrl: '/api', fetchImplementation: fetchMock })

    await expect(collect(service.streamChat(body))).rejects.toMatchObject({ status: 429, retryAfterSeconds: 12 })
  })

  it('keeps the status code when the error body is not JSON', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response('<html>nope</html>', { status: 503 }))
    const service = createAiTutorService(vi.fn(), { baseUrl: '/api', fetchImplementation: fetchMock })

    await expect(collect(service.streamChat(body))).rejects.toMatchObject({ status: 503 })
  })
})
