import assert from 'node:assert/strict'
import test from 'node:test'
import { createApiClient, ApiError } from '../src/services/apiClient.ts'
import { createServices } from '../src/services/createServices.ts'
import { createMockFetch } from '../src/services/mocks/mockFetch.ts'

function mockServices() {
  return createServices(createApiClient('/api', createMockFetch()))
}

test('the same feature services load courses, users, modules and enrollments from mock transport', async () => {
  const { courses, users, modules, enrollments } = mockServices()
  const list = await courses.getCourses()
  assert.equal(list.length, 2)
  assert.deepEqual(await courses.getCourse(1), list[0])
  assert.equal((await users.getUsers()).length, 2)
  assert.equal((await users.getUser('student@example.com')).name, 'Example Student')
  assert.equal((await modules.getModules(1))[1].dependencyIds[0], 1)
  assert.equal((await modules.getModule(2)).title, 'Persistence')
  assert.equal((await enrollments.getEnrollment(1, 'student@example.com')).role, 'STUDENT')
  assert.equal((await users.getUserEnrollments('student@example.com'))[0].courseId, 1)
})

test('mock filters and empty course results match the API shapes', async () => {
  const { enrollments, modules } = mockServices()
  assert.equal((await enrollments.getEnrollments(1)).length, 2)
  assert.deepEqual((await enrollments.getEnrollments(1, 'TEACHER')).map(item => item.email),
    ['teacher@example.com'])
  assert.deepEqual(await enrollments.getEnrollments(2), [])
  assert.deepEqual(await modules.getModules(2), [])
})

test('mock responses cannot be mutated to corrupt subsequent requests', async () => {
  const { courses, modules } = mockServices()
  const list = await courses.getCourses()
  list[0].title = 'Changed locally'
  const module = await modules.getModule(2)
  module.dependencyIds.push(99)
  assert.equal((await courses.getCourse(1)).title, 'DAT250')
  assert.deepEqual((await modules.getModule(2)).dependencyIds, [1])
})

test('missing mock resources use the shared HTTP error type', async () => {
  const { courses, users, modules, enrollments } = mockServices()
  for (const operation of [() => courses.getCourse(999), () => users.getUser('missing@example.com'),
    () => modules.getModule(999), () => enrollments.getEnrollments(999)]) {
    await assert.rejects(operation(), error => error instanceof ApiError && error.status === 404)
  }
})

test('mock writes explicitly fail instead of pretending to save', async () => {
  const { courses } = mockServices()
  await assert.rejects(courses.createCourse({ title: 'New', description: '', term: 'AUTUMN', year: 2026 }),
    error => error instanceof ApiError && error.status === 501 && error.message.includes('read-only'))
})

test('real transport encodes email paths and preserves bodies, methods, CSRF headers and role filters', async () => {
  const calls = []
  const services = createServices(createApiClient('/api', async (url, options) => {
    calls.push({ url, ...options })
    return Response.json({})
  }))
  const email = 'student+web@example.com'
  const encoded = encodeURIComponent(email)
  await services.users.getUser(email)
  assert.equal(calls.at(-1).url, `/api/users/${encoded}`)
  await services.enrollments.getEnrollments(1, 'STUDENT')
  assert.equal(calls.at(-1).url, '/api/courses/1/enrollments?role=STUDENT')
  await services.enrollments.changeRole(1, email, 'TEACHER', { headers: { 'X-CSRF-TOKEN': 'token' } })
  assert.equal(calls.at(-1).url, `/api/courses/1/enrollments/${encoded}`)
  assert.equal(calls.at(-1).method, 'PUT')
  assert.deepEqual(JSON.parse(calls.at(-1).body), { email, role: 'TEACHER' })
  assert.equal(calls.at(-1).headers.get('X-CSRF-TOKEN'), 'token')
  await services.modules.addDependency(3, 2)
  assert.equal(calls.at(-1).url, '/api/modules/3/dependencies/2')
  assert.equal(calls.at(-1).method, 'POST')
  await services.modules.removeDependency(3, 2)
  assert.equal(calls.at(-1).method, 'DELETE')
})

test('service deletion preserves the controller-specific response contracts', async () => {
  const response = { deletedModuleId: 1, affectedModuleIds: [2] }
  const services = createServices(createApiClient('/api', async url =>
    url === '/api/modules/1' ? Response.json(response) : new Response(null, { status: 204 })))
  assert.equal(await services.courses.deleteCourse(1), undefined)
  assert.equal(await services.users.deleteUser('student@example.com'), undefined)
  assert.equal(await services.enrollments.unenroll(1, 'student@example.com'), undefined)
  assert.deepEqual(await services.modules.deleteModule(1), response)
})

test('mock reads respect request cancellation', async () => {
  const controller = new AbortController()
  controller.abort()
  await assert.rejects(mockServices().courses.getCourses({ signal: controller.signal }),
    error => error.name === 'AbortError')
})
