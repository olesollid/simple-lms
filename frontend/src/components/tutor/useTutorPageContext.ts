import { useEffect, useState } from 'react'
import { contents, courses } from '../../services'
import type { TutorPageContext } from '../../types/aiTutor'
import { routeKey } from './tutorRoute'
import type { TutorRoute } from './tutorRoute'
import type { TutorNames } from './tutorTypes'

interface Resolved {
  courseName: string
  assignmentName: string | null
  assignmentId: number | null
}

type Result = { ok: true; value: Resolved } | { ok: false }

/**
 * What the student is looking at: the page type and ids to send to the backend, plus the course
 * and assignment names (from the LMS API) used for greetings. The ids are only hints; the backend
 * looks names up itself and re-checks access.
 */
export function useTutorPageContext(route: TutorRoute): {
  context: TutorPageContext
  names: TutorNames
  retry: () => void
} {
  const key = routeKey(route)
  const [results, setResults] = useState<Record<string, Result>>({})
  const needsLookup = route.kind === 'course' || route.kind === 'content'
  const result = results[key]

  const courseId = route.kind === 'course' || route.kind === 'content' ? route.courseId : null
  const contentId = route.kind === 'content' ? route.contentId : null

  useEffect(() => {
    if (!needsLookup || result || courseId == null) return
    const controller = new AbortController()
    const options = { signal: controller.signal }

    ;(async (): Promise<Result> => {
      try {
        if (contentId == null) {
          const course = await courses.getCourse(courseId, options)
          return { ok: true, value: { courseName: course.title, assignmentName: null, assignmentId: null } }
        }
        const [content, course] = await Promise.all([
          contents.getContent(contentId, options),
          courses.getCourse(courseId, options),
        ])
        const isAssignment = content.type === 'Assignment' || content.type === 'CodingExercise'
        return {
          ok: true,
          value: {
            courseName: course.title,
            assignmentName: isAssignment ? content.title : null,
            assignmentId: isAssignment ? content.id : null,
          },
        }
      } catch {
        return { ok: false }
      }
    })().then(value => {
      if (!controller.signal.aborted) setResults(previous => ({ ...previous, [key]: value }))
    })

    return () => controller.abort()
  }, [key, needsLookup, result, courseId, contentId])

  function retry() {
    setResults(previous => Object.fromEntries(
      Object.entries(previous).filter(([entryKey]) => entryKey !== key),
    ))
  }

  const resolved = result?.ok ? result.value : null
  const names: TutorNames = !needsLookup
    ? { status: 'ready', courseName: null, assignmentName: null }
    : !result
      ? { status: 'loading', courseName: null, assignmentName: null }
      : resolved
        ? { status: 'ready', courseName: resolved.courseName, assignmentName: resolved.assignmentName }
        : { status: 'error', courseName: null, assignmentName: null }

  let context: TutorPageContext
  if (route.kind === 'dashboard') {
    context = { page_type: 'dashboard', course_id: null, assignment_id: null }
  } else if (route.kind === 'other') {
    context = { page_type: 'other', course_id: null, assignment_id: null }
  } else if (resolved?.assignmentId != null) {
    context = { page_type: 'assignment', course_id: route.courseId, assignment_id: resolved.assignmentId }
  } else {
    // While names load (or if they fail) a content page counts as a plain course page.
    context = { page_type: 'course', course_id: route.courseId, assignment_id: null }
  }

  return { context, names, retry }
}
