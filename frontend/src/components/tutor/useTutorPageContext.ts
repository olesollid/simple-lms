import { useEffect, useState } from 'react'
import { courses } from '../../services'
import type { TutorPageContext } from '../../types/aiTutor'
import { routeKey } from './tutorRoute'
import type { TutorRoute } from './tutorRoute'
import type { TutorNames } from './tutorTypes'

type Result = { ok: true; courseName: string } | { ok: false }

/**
 * Whether the student is inside a course, for the backend, plus that course's name (from the LMS
 * API) for greetings. The id is only a hint; the backend looks the name up itself and re-checks
 * enrollment.
 */
export function useTutorPageContext(route: TutorRoute): {
  context: TutorPageContext
  names: TutorNames
  retry: () => void
} {
  const key = routeKey(route)
  const [results, setResults] = useState<Record<string, Result>>({})
  const courseId = route.kind === 'course' ? route.courseId : null
  const result = results[key]

  useEffect(() => {
    if (courseId == null || result) return
    const controller = new AbortController()

    ;(async (): Promise<Result> => {
      try {
        const course = await courses.getCourse(courseId, { signal: controller.signal })
        return { ok: true, courseName: course.title }
      } catch {
        return { ok: false }
      }
    })().then(value => {
      if (!controller.signal.aborted) setResults(previous => ({ ...previous, [key]: value }))
    })

    return () => controller.abort()
  }, [key, courseId, result])

  function retry() {
    setResults(previous => Object.fromEntries(
      Object.entries(previous).filter(([entryKey]) => entryKey !== key),
    ))
  }

  const names: TutorNames = courseId == null
    ? { status: 'ready', courseName: null }
    : !result
      ? { status: 'loading', courseName: null }
      : result.ok
        ? { status: 'ready', courseName: result.courseName }
        : { status: 'error', courseName: null }

  const context: TutorPageContext = route.kind === 'course'
    ? { page_type: 'course', course_id: route.courseId }
    : { page_type: route.kind, course_id: null }

  return { context, names, retry }
}
