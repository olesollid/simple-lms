import { useEffect, useState } from 'react'
import { aiTutor, users } from '../../services'

/**
 * Whether the tutor is on for this student here, and who they are. The backend decides (the global
 * flag and enrollment as a student); any failure counts as off, so the widget never shows up
 * unless the server explicitly says so. Results are cached per course for the session.
 */
export function useTutorAccess(courseId: number | null): { enabled: boolean; email: string | null } {
  const key = courseId == null ? 'global' : String(courseId)
  const [flags, setFlags] = useState<Record<string, boolean>>({})
  const [email, setEmail] = useState<string | null>(null)
  const enabled = flags[key] === true
  const known = key in flags

  useEffect(() => {
    if (known) return
    const controller = new AbortController()
    // The async wrapper turns synchronous failures (such as a missing service) into "off" too.
    ;(async () => {
      try {
        const config = await aiTutor.getConfig(courseId, { signal: controller.signal })
        return config.enabled === true
      } catch {
        return false
      }
    })().then(value => {
      if (!controller.signal.aborted) setFlags(previous => ({ ...previous, [key]: value }))
    })
    return () => controller.abort()
  }, [key, courseId, known])

  useEffect(() => {
    if (!enabled || email) return
    const controller = new AbortController()
    ;(async () => {
      try {
        return (await users.getCurrentUser({ signal: controller.signal })).email
      } catch {
        return null
      }
    })().then(value => {
      if (!controller.signal.aborted && value) setEmail(value)
    })
    return () => controller.abort()
  }, [enabled, email])

  return { enabled, email }
}
