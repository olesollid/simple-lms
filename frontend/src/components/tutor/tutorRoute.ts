import { matchPath } from 'react-router'

export type TutorRoute =
  | { kind: 'dashboard' }
  | { kind: 'course'; courseId: number }
  | { kind: 'other' }

function toId(value: string | undefined): number | null {
  if (!value || !/^\d+$/.test(value)) return null
  return Number(value)
}

/**
 * Maps a URL path to "inside a course (which one)" or not. Anything under a course, including
 * its module and content pages, counts as the course itself: the tutor never learns which
 * assignment or page the student is on. The id is only a hint; the backend verifies it.
 */
export function parseTutorRoute(pathname: string): TutorRoute {
  const path = pathname.replace(/\/+$/, '') || '/'
  if (path === '/' || path === '/courses' || path === '/dashboard') return { kind: 'dashboard' }

  const course = matchPath({ path: '/courses/:courseId', end: false }, path)
  if (course) {
    const courseId = toId(course.params.courseId)
    if (courseId != null) return { kind: 'course', courseId }
  }
  return { kind: 'other' }
}

export function routeCourseId(route: TutorRoute): number | null {
  return route.kind === 'course' ? route.courseId : null
}

export function routeKey(route: TutorRoute): string {
  return route.kind === 'course' ? `course:${route.courseId}` : route.kind
}
