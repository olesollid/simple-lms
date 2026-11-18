import { describe, expect, it } from 'vitest'
import { ApiError, ApiResponseError } from '../services/apiClient'
import { contentDetailError } from './contentDetailState'

describe('contentDetailError', () => {
  it('asks the user to sign in on 401', () => {
    expect(contentDetailError(new ApiError(401, 'x', null))).toMatch(/sign in/i)
  })

  it('explains missing permission on 403', () => {
    expect(contentDetailError(new ApiError(403, 'x', null))).toMatch(/permission/i)
  })

  it('reports missing content on 404', () => {
    expect(contentDetailError(new ApiError(404, 'x', null))).toMatch(/could not be found/i)
  })

  it('passes through other API error messages', () => {
    expect(contentDetailError(new ApiError(500, 'Boom', null))).toBe('Boom')
  })

  it('reports a non-JSON response', () => {
    expect(contentDetailError(new ApiResponseError(200))).toMatch(/unexpected response/i)
  })

  it('falls back to a connection message for unknown errors', () => {
    expect(contentDetailError(new TypeError('Failed to fetch'))).toMatch(/connection/i)
  })
})
