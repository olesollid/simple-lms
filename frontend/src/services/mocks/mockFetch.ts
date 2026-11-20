import type { CourseResponse } from '../../types/course.ts'
import type { UserResponse } from '../../types/user.ts'
import type { EnrollmentResponse } from '../../types/enrollment.ts'
import type { ModuleResponse } from '../../types/module.ts'

// Read-only fixtures for UI development, not a simulation of backend security.
const courses: CourseResponse[] = [
  { id: 1, title: 'DAT250', description: 'Advanced Software Technologies', term: 'AUTUMN', year: 2026 },
  { id: 2, title: 'INF100', description: 'Introduction to Programming', term: 'AUTUMN', year: 2026 },
]
const users: UserResponse[] = [
  { email: 'student@example.com', name: 'Example Student' },
  { email: 'teacher@example.com', name: 'Example Teacher' },
]
const modules: ModuleResponse[] = [
  { id: 1, courseId: 1, title: 'Introduction', dependencyIds: [], locked: false },
  { id: 2, courseId: 1, title: 'Persistence', dependencyIds: [1], locked: false },
]
const enrollments: EnrollmentResponse[] = [
  { id: 1, courseId: 1, courseTitle: 'DAT250', email: 'student@example.com',
    name: 'Example Student', role: 'STUDENT', enrolledAt: '2026-08-17T09:00:00' },
  { id: 2, courseId: 1, courseTitle: 'DAT250', email: 'teacher@example.com',
    name: 'Example Teacher', role: 'TEACHER', enrolledAt: '2026-08-17T09:00:00' },
]

export function createMockFetch(): typeof fetch {
  return async (input, options) => {
    const original = input instanceof Request ? input : undefined
    const signal = options?.signal ?? original?.signal
    signal?.throwIfAborted()
    const method = (options?.method ?? original?.method ?? 'GET').toUpperCase()
    if (method !== 'GET') {
      return Response.json({ message: 'Mock mode is read-only. Switch to the real API to save changes.' },
        { status: 501 })
    }

    // The central selector always uses /api as the mock transport base.
    const url = new URL(original?.url ?? String(input), 'http://mock.local')
    const [root, resource, key, relation, member] = url.pathname.split('/').filter(Boolean)
    const missing = () => Response.json({ message: 'Mock resource not found' }, { status: 404 })
    const result = (value: unknown) => value === undefined ? missing() : Response.json(value)
    if (root !== 'api') return missing()

    if (resource === 'courses') {
      if (!key) return result(courses)
      const course = courses.find(item => item.id === Number(key))
      if (!course) return missing()
      if (!relation) return result(course)
      if (relation === 'modules' && !member) {
        return result(modules.filter(item => item.courseId === course.id))
      }
      if (relation === 'enrollments') {
        const items = enrollments.filter(item => item.courseId === course.id)
        if (member) return result(items.find(item => item.email === decodeURIComponent(member).toLowerCase()))
        const role = url.searchParams.get('role')
        if (role && role !== 'STUDENT' && role !== 'TEACHER') {
          return Response.json({ message: 'Malformed request' }, { status: 400 })
        }
        return result(items.filter(item => !role || item.role === role))
      }
    }
    if (resource === 'users') {
      if (!key) return missing()
      // A fixed sample identity; mock mode does not simulate OAuth sessions.
      if (key === 'me' && !relation) return result(users[0])
      const user = users.find(item => item.email === decodeURIComponent(key).toLowerCase())
      if (!user) return missing()
      if (!relation) return result(user)
      if (relation === 'enrollments') return result(enrollments.filter(item => item.email === user.email))
    }
    if (resource === 'modules' && key && !relation) {
      return result(modules.find(item => item.id === Number(key)))
    }
    return missing()
  }
}
