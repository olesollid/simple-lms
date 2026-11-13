import assert from 'node:assert/strict'
import { test } from 'vitest'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import CourseList from '../src/components/CourseList.tsx'
import { courseListError } from '../src/pages/courseListState.ts'
import { ApiError, ApiResponseError } from '../src/services/apiClient.ts'

const render = state => renderToStaticMarkup(createElement(CourseList, { state }))

test('loading is announced without showing an empty result', () => {
  const html = render({ status: 'loading' })
  assert.match(html, /role="status"/)
  assert.match(html, /Loading courses/)
  assert.doesNotMatch(html, /No courses yet/)
})

test('an empty API result shows the empty state', () => {
  const html = render({ status: 'success', courses: [] })
  assert.match(html, /No courses yet/)
  assert.doesNotMatch(html, /course-card/)
})

test('course cards display API values, semesters and accessible headings', () => {
  const html = render({ status: 'success', courses: [
    { id: 9, title: 'DAT250', description: 'Software technologies', term: 'AUTUMN', year: 2026 },
    { id: 10, title: 'INF100', description: 'Programming', term: 'SPRING', year: 2027 },
  ] })
  assert.match(html, /2 courses/)
  assert.match(html, /Autumn 2026/)
  assert.match(html, /Spring 2027/)
  assert.match(html, /aria-labelledby="course-9"/)
  assert.match(html, /Software technologies/)
  assert.match(html, /INF100/)
})

test('missing descriptions have a fallback and server content is escaped', () => {
  const html = render({ status: 'success', courses: [
    { id: 1, title: '<script>alert(1)</script>', description: ' ', term: 'AUTUMN', year: 2026 },
  ] })
  assert.match(html, /1 course</)
  assert.match(html, /No description provided/)
  assert.doesNotMatch(html, /<script>/)
})

test('errors are announced with an alert rather than an empty course list', () => {
  const html = render({ status: 'error', message: 'Please sign in to view courses, then try again.' })
  assert.match(html, /role="alert"/)
  assert.match(html, /Please sign in/)
  assert.doesNotMatch(html, /No courses yet/)
})

test('authentication, permissions, malformed responses and network failures have useful messages', () => {
  assert.match(courseListError(new ApiError(401, 'Unauthorized', null)), /sign in/)
  assert.match(courseListError(new ApiError(403, 'Forbidden', null)), /permission/)
  assert.equal(courseListError(new ApiError(500, 'Service unavailable', null)), 'Service unavailable')
  assert.match(courseListError(new ApiResponseError(200)), /unexpected response/)
  assert.match(courseListError(new TypeError('fetch failed')), /connection/)
})
