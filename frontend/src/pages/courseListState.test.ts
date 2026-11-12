import { describe, expect, it } from 'vitest'
import { ApiError, ApiResponseError } from '../services/apiClient'
import { courseListError } from './courseListState'

describe('courseListError', () => {
  it('asks the user to sign in on 401', () => {
    expect(courseListError(new ApiError(401, 'x', null))).toMatch(/sign in/i)
  })

  it('explains missing permission on 403', () => {
    expect(courseListError(new ApiError(403, 'x', null))).toMatch(/permission/i)
  })

  it('passes through other API error messages', () => {
    expect(courseListError(new ApiError(500, 'Boom', null))).toBe('Boom')
  })

  it('reports a non-JSON response', () => {
    expect(courseListError(new ApiResponseError(200))).toMatch(/unexpected response/i)
  })

  it('falls back to a connection message for unknown errors', () => {
    expect(courseListError(new TypeError('Failed to fetch'))).toMatch(/connection/i)
  })
})
