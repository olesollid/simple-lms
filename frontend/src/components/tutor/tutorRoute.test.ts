import { describe, expect, it } from 'vitest'
import { parseTutorRoute, routeCourseId } from './tutorRoute'

describe('parseTutorRoute', () => {
  it('treats the root, dashboard and course list as the dashboard', () => {
    expect(parseTutorRoute('/')).toEqual({ kind: 'dashboard' })
    expect(parseTutorRoute('/dashboard')).toEqual({ kind: 'dashboard' })
    expect(parseTutorRoute('/courses')).toEqual({ kind: 'dashboard' })
    expect(parseTutorRoute('/courses/')).toEqual({ kind: 'dashboard' })
  })

  it('recognises a course page', () => {
    expect(parseTutorRoute('/courses/12')).toEqual({ kind: 'course', courseId: 12 })
  })

  it('recognises a content page with both ids', () => {
    expect(parseTutorRoute('/courses/12/modules/3/contents/45'))
      .toEqual({ kind: 'content', courseId: 12, contentId: 45 })
  })

  it('treats anything else, including malformed ids, as other', () => {
    expect(parseTutorRoute('/profile')).toEqual({ kind: 'other' })
    expect(parseTutorRoute('/courses/abc')).toEqual({ kind: 'other' })
    expect(parseTutorRoute('/courses/1/modules/2/contents/x')).toEqual({ kind: 'other' })
  })

  it('exposes the course id only for course and content pages', () => {
    expect(routeCourseId({ kind: 'course', courseId: 4 })).toBe(4)
    expect(routeCourseId({ kind: 'content', courseId: 4, contentId: 9 })).toBe(4)
    expect(routeCourseId({ kind: 'dashboard' })).toBeNull()
    expect(routeCourseId({ kind: 'other' })).toBeNull()
  })
})
