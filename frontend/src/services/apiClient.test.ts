import { afterEach, describe, expect, it } from 'vitest'
import { createApiClient } from './apiClient'

afterEach(() => {
  // jsdom has no API to clear all cookies; expire anything a test set.
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
})

describe('createApiClient CSRF header', () => {
  it('sends the XSRF-TOKEN cookie as an X-XSRF-TOKEN header on writes', async () => {
    document.cookie = 'XSRF-TOKEN=abc123'
    let sentHeader: string | null = null
    const request = createApiClient('/api', async (_url, options) => {
      sentHeader = (options?.headers as Headers).get('X-XSRF-TOKEN')
      return Response.json({ id: 1 }, { status: 201 })
    })

    await request('courses', { method: 'POST', body: { title: 'DAT250' } })

    expect(sentHeader).toBe('abc123')
  })

  it('does not send an X-XSRF-TOKEN header on GET requests', async () => {
    document.cookie = 'XSRF-TOKEN=abc123'
    let hadHeader = true
    const request = createApiClient('/api', async (_url, options) => {
      hadHeader = (options?.headers as Headers).has('X-XSRF-TOKEN')
      return Response.json([])
    })

    await request('courses')

    expect(hadHeader).toBe(false)
  })

  it('does not overwrite an explicitly supplied X-XSRF-TOKEN header', async () => {
    document.cookie = 'XSRF-TOKEN=abc123'
    let sentHeader: string | null = null
    const request = createApiClient('/api', async (_url, options) => {
      sentHeader = (options?.headers as Headers).get('X-XSRF-TOKEN')
      return Response.json({ id: 1 }, { status: 201 })
    })

    await request('courses', {
      method: 'POST',
      body: { title: 'DAT250' },
      headers: { 'X-XSRF-TOKEN': 'manual-token' },
    })

    expect(sentHeader).toBe('manual-token')
  })

  it('omits the header when no XSRF-TOKEN cookie is set', async () => {
    let hadHeader = true
    const request = createApiClient('/api', async (_url, options) => {
      hadHeader = (options?.headers as Headers).has('X-XSRF-TOKEN')
      return Response.json({ id: 1 }, { status: 201 })
    })

    await request('courses', { method: 'POST', body: { title: 'DAT250' } })

    expect(hadHeader).toBe(false)
  })
})
