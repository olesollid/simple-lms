import { useLocation } from 'react-router'
import TutorChat from './TutorChat'
import { parseTutorRoute, routeCourseId } from './tutorRoute'
import { useTutorAccess } from './useTutorAccess'

/**
 * Renders nothing unless the backend says the tutor is on for this student and page. The chat
 * itself mounts only then, so a disabled tutor makes no further requests.
 */
export default function TutorWidget() {
  const { pathname } = useLocation()
  const route = parseTutorRoute(pathname)
  const { enabled, email } = useTutorAccess(routeCourseId(route))
  if (!enabled || !email) return null
  return <TutorChat key={email} email={email} route={route} />
}
