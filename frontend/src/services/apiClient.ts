export interface ApiRequestOptions extends Omit<RequestInit, 'body' | 'credentials'> {
  body?: unknown
}

export type ApiRequest = ReturnType<typeof createApiClient>
export type ApiCallOptions = Pick<ApiRequestOptions, 'headers' | 'signal'>

export class ApiError extends Error {
  readonly status: number
  readonly details: unknown

  constructor(status: number, message: string, details: unknown) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.details = details
  }
}

export class ApiResponseError extends Error {
  readonly status: number

  constructor(status: number) {
    super('The API returned an unexpected response instead of JSON')
    this.name = 'ApiResponseError'
    this.status = status
  }
}

// Methods Spring Security's CSRF filter doesn't require a token for.
const CSRF_SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS', 'TRACE'])

// Reads the XSRF-TOKEN cookie Spring Security's csrf().spa() writes after login.
// Exported for callers outside the /api-scoped request client, such as POST /logout.
export function readXsrfToken(): string | undefined {
  if (typeof document === 'undefined') return undefined
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/)
  return match ? decodeURIComponent(match[1]) : undefined
}

// A separate factory lets tests and future mock services supply their own fetch.
export function createApiClient(baseUrl: string, fetchImplementation: typeof fetch = fetch) {
  const base = baseUrl.trim().replace(/\/+$/, '')

  return async function request<T>(path: string, options: ApiRequestOptions = {}): Promise<T> {
    const { body, headers: suppliedHeaders, ...requestOptions } = options
    const isFormData = body instanceof FormData
    const headers = new Headers(suppliedHeaders)
    if (!headers.has('Accept')) headers.set('Accept', 'application/json')
    if (body !== undefined && !isFormData && !headers.has('Content-Type')) {
      headers.set('Content-Type', 'application/json')
    }

    const method = (requestOptions.method ?? 'GET').toUpperCase()
    if (!CSRF_SAFE_METHODS.has(method) && !headers.has('X-XSRF-TOKEN')) {
      const token = readXsrfToken()
      if (token) headers.set('X-XSRF-TOKEN', token)
    }

    const response = await fetchImplementation(`${base}/${path.replace(/^\/+/, '')}`, {
      ...requestOptions,
      headers,
      credentials: 'include',
      body: isFormData ? body : body === undefined ? undefined : JSON.stringify(body),
    })

    // Read once. Error responses may be empty, plain text, or an HTML login page.
    const text = await response.text()
    let data: unknown
    let isJson = false
    if (text.trim()) {
      try {
        data = JSON.parse(text)
        isJson = true
      } catch {
        // Keep the HTTP status even when an error response is not JSON.
      }
    }

    if (!response.ok) {
      const message = typeof data === 'object' && data !== null && 'message' in data
        && typeof data.message === 'string' && data.message.trim()
        ? data.message
        : `Request failed (${response.status})`
      throw new ApiError(response.status, message, data)
    }

    // Call request<void> for endpoints returning no body, such as DELETE courses.
    if (response.status === 204 || response.status === 205 || !text.trim()) {
      return undefined as T
    }
    if (!isJson) throw new ApiResponseError(response.status)

    // T describes the DTO contract; this assertion is not runtime validation.
    return data as T
  }
}
