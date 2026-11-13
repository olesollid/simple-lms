import assert from 'node:assert/strict'
import { test } from 'vitest'
import { ApiError, ApiResponseError, createApiClient } from '../src/services/apiClient.ts'

test('joins the base URL, sends cookies, and returns JSON', async () => {
  const request = createApiClient('https://example.com/api/', async (url, options) => {
    assert.equal(url, 'https://example.com/api/courses')
    assert.equal(options.credentials, 'include')
    assert.equal(options.headers.get('Accept'), 'application/json')
    assert.equal(options.headers.has('Content-Type'), false)
    return Response.json([{ id: 1, title: 'DAT250' }])
  })
  assert.deepEqual(await request('/courses'), [{ id: 1, title: 'DAT250' }])
})

test('serializes request bodies and forwards custom CSRF headers and abort signals', async () => {
  const controller = new AbortController()
  const request = createApiClient('/api', async (url, options) => {
    assert.equal(url, '/api/courses')
    assert.equal(options.method, 'POST')
    assert.equal(options.headers.get('Content-Type'), 'application/json')
    assert.equal(options.headers.get('X-XSRF-TOKEN'), 'token-from-auth-layer')
    assert.equal(options.signal, controller.signal)
    assert.deepEqual(JSON.parse(options.body), { title: 'DAT250' })
    return Response.json({ id: 1 }, { status: 201 })
  })
  await request('courses', {
    method: 'POST', body: { title: 'DAT250' },
    headers: { 'X-XSRF-TOKEN': 'token-from-auth-layer' }, signal: controller.signal,
  })
})

test('handles empty 204 responses without attempting JSON parsing', async () => {
  const request = createApiClient('/api', async () => new Response(null, { status: 204 }))
  assert.equal(await request('/courses/1', { method: 'DELETE' }), undefined)
})

test('keeps JSON returned by module deletion', async () => {
  const result = { deletedModuleId: 1, affectedModuleIds: [2, 3] }
  const request = createApiClient('/api', async () => Response.json(result))
  assert.deepEqual(await request('/modules/1', { method: 'DELETE' }), result)
})

test('exposes backend error messages and status codes', async () => {
  const request = createApiClient('/api', async () =>
    Response.json({ message: 'Course not found: 9' }, { status: 404 }))
  await assert.rejects(request('/courses/9'), (error) => {
    assert.ok(error instanceof ApiError)
    assert.equal(error.status, 404)
    assert.equal(error.message, 'Course not found: 9')
    return true
  })
})

test('preserves 401 status when the server returns HTML instead of JSON', async () => {
  const request = createApiClient('/api', async () =>
    new Response('<html>Login</html>', { status: 401 }))
  await assert.rejects(request('/courses'), (error) => {
    assert.ok(error instanceof ApiError)
    assert.equal(error.status, 401)
    assert.equal(error.message, 'Request failed (401)')
    return true
  })
})

test('rejects successful HTML responses such as redirected login pages', async () => {
  const request = createApiClient('/api', async () => new Response('<html>Login</html>'))
  await assert.rejects(request('/courses'), ApiResponseError)
})

test('keeps network failures distinguishable from HTTP errors', async () => {
  const failure = new TypeError('Failed to fetch')
  const request = createApiClient('/api', async () => { throw failure })
  await assert.rejects(request('/courses'), (error) => error === failure)
})
