import { readXsrfToken } from './apiClient.ts'

// POST /logout is served outside the /api-scoped client by Spring Security's own
// logout filter, so it is called directly rather than through the shared request client.
export async function logout(): Promise<void> {
  const headers = new Headers()
  const token = readXsrfToken()
  if (token) headers.set('X-XSRF-TOKEN', token)
  await fetch('/logout', { method: 'POST', credentials: 'include', headers })
}
