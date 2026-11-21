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

  it('treats anything inside a course, including content pages, as just the course', () => {
    expect(parseTutorRoute('/courses/12/modules/3/contents/45')).toEqual({ kind: 'course', courseId: 12 })
    expect(parseTutorRoute('/courses/12/anything/else')).toEqual({ kind: 'course', courseId: 12 })
  })

  it('treats anything else, including malformed ids, as other', () => {
    expect(parseTutorRoute('/profile')).toEqual({ kind: 'other' })
    expect(parseTutorRoute('/courses/abc')).toEqual({ kind: 'other' })
    expect(parseTutorRoute('/courses/abc/modules/1/contents/2')).toEqual({ kind: 'other' })
  })

  it('exposes the course id only inside a course', () => {
    expect(routeCourseId({ kind: 'course', courseId: 4 })).toBe(4)
    expect(routeCourseId({ kind: 'dashboard' })).toBeNull()
    expect(routeCourseId({ kind: 'other' })).toBeNull()
  })
})
