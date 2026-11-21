import type { TutorPageContext } from '../../types/aiTutor'
import type { TutorNames } from './tutorTypes'

/** The opening line for the current page. The name comes from the LMS API; ids alone are never shown. */
export function greetingText(context: TutorPageContext, names: TutorNames): string {
  if (context.page_type === 'course') {
    return `I see you're working on ${names.courseName ?? 'this course'}. How can I help?`
  }
  return "Hi! I'm your AI study assistant. Ask me about concepts you're studying "
    + 'or your courses. How can I help?'
}

export function noticeText(names: TutorNames): string {
  return names.courseName ? `You're now in ${names.courseName}` : "You're now in a different course"
}
