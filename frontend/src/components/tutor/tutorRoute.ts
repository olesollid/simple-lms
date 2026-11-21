import { matchPath } from 'react-router'

export type TutorRoute =
  | { kind: 'dashboard' }
  | { kind: 'course'; courseId: number }
  | { kind: 'content'; courseId: number; contentId: number }
  | { kind: 'other' }

function toId(value: string | undefined): number | null {
  if (!value || !/^\d+$/.test(value)) return null
  return Number(value)
}

/** Maps a URL path to the kind of page the student is on. Ids are only hints; the backend verifies them. */
export function parseTutorRoute(pathname: string): TutorRoute {
  const path = pathname.replace(/\/+$/, '') || '/'
  if (path === '/' || path === '/courses' || path === '/dashboard') return { kind: 'dashboard' }

  const content = matchPath('/courses/:courseId/modules/:moduleId/contents/:contentId', path)
  if (content) {
    const courseId = toId(content.params.courseId)
    const contentId = toId(content.params.contentId)
    if (courseId != null && contentId != null) return { kind: 'content', courseId, contentId }
  }

  const course = matchPath('/courses/:courseId', path)
  if (course) {
    const courseId = toId(course.params.courseId)
    if (courseId != null) return { kind: 'course', courseId }
  }
  return { kind: 'other' }
}

export function routeCourseId(route: TutorRoute): number | null {
  return route.kind === 'course' || route.kind === 'content' ? route.courseId : null
}

export function routeKey(route: TutorRoute): string {
  switch (route.kind) {
    case 'course': return `course:${route.courseId}`
    case 'content': return `content:${route.courseId}:${route.contentId}`
    default: return route.kind
  }
}
