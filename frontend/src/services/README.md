# Services

`api.ts` exports the configured `apiRequest` function. It uses
`VITE_API_BASE_URL`, defaulting to `/api`. Vite proxies `/api` to Spring Boot
on port 8080 during development. The production host must route `/api` to the
backend, or the build must specify an absolute API base URL.

```typescript
import { apiRequest, ApiError } from './api'
import type { CourseResponse, CourseRequest } from '../types/course'

// Low-level examples; pages should use the feature services described below.
export const getCourses = () => apiRequest<CourseResponse[]>('/courses')
export const createCourse = (body: CourseRequest) =>
  apiRequest<CourseResponse>('/courses', { method: 'POST', body })
export const deleteCourse = (id: number) =>
  apiRequest<void>(`/courses/${id}`, { method: 'DELETE' })
```

The client serializes JSON bodies, sends session cookies, and accepts custom
headers and AbortSignals. Supply only the path after the base URL (`/courses`,
not `/api/courses`). Responses containing JSON are returned as the requested
type; an empty response returns undefined. Use `void` for no-content endpoints.
Module deletion is different: request its ModuleDeleteResponse JSON type.
Type arguments describe expected DTOs; they do not validate incoming JSON.

Non-success HTTP responses throw ApiError with `status`, `message`, and
`details`. The message comes from the backend's `{ message }` error format when
available, with an HTTP-status fallback for empty/non-JSON errors. Network
failures and cancelled requests retain their native fetch errors.
An unexpected non-JSON success throws ApiResponseError (for example, HTML from
a login redirect). The client does not redirect users or manage login state.

Session cookies alone do not complete authentication. The auth layer must obtain
and refresh a CSRF token and pass its agreed header through `headers` on requests
that modify data. No token endpoint or header convention is assumed here.
The auth layer should handle ApiError status 401; authorization/CSRF failures may
return 403. For a cross-origin API, backend CORS and cookie settings must also
permit credentialed requests. Keep `/api` for the simple same-origin setup.

## Feature services and mock mode

Pages import configured feature services from `index.ts`:

```typescript
import { courses, enrollments, modules, users } from '../services'

const courseList = await courses.getCourses()
const course = await courses.getCourse(1)
const students = await enrollments.getEnrollments(1, 'STUDENT')
const courseModules = await modules.getModules(1)
const user = await users.getUser('student@example.com')
```

`courses.ts`, `users.ts`, `enrollments.ts`, and `modules.ts` cover the current
controllers' read, create, update and delete operations, including module
dependencies and enrollments by user. Email path segments are URL-encoded.
Each function accepts optional request headers and an AbortSignal as its last
argument, allowing the authentication layer to provide CSRF headers.
For example: `courses.createCourse(body, { headers: csrfHeaders })`.

For read-only sample data without the backend, set this in `.env.local`:

```dotenv
VITE_USE_MOCK_API=true
```

Restart Vite. Set it to `false` (the default) to use the real API. The central
selector changes only the request transport: pages use exactly the same service
functions, return types, and ApiError handling in either mode.

Mock data includes courses 1 and 2, student@example.com, teacher@example.com,
two modules and two enrollments in course 1. Course 2 has no modules/enrollments
for empty-state development. Unknown resources return 404. Every non-GET request
returns 501 with a clear read-only message. The mock does not authenticate users,
simulate persistence, enforce course permissions, or replace backend tests.
It intercepts only these service calls, not arbitrary browser fetch requests.

`createServices(request)` builds the service set from a shared request function.
`createApiClient(baseUrl, fetchImplementation)` lets tests supply either a mock
transport or a request-recording fetch without loading browser environment settings.
CoursesPage calls `courses.getCourses()` through this selector on mount and refresh.
