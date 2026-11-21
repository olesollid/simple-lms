import type { TutorPageContext } from '../../types/aiTutor'
import type { TutorNames } from './tutorTypes'

/** The opening line for the current page. Names come from the LMS API; ids alone are never shown. */
export function greetingText(context: TutorPageContext, names: TutorNames): string {
  const course = names.courseName ?? 'this course'
  if (context.page_type === 'assignment') {
    const assignment = names.assignmentName ? `the assignment "${names.assignmentName}"` : 'this assignment'
    return `I see you're working on ${assignment} in ${course}. How can I help?`
  }
  if (context.page_type === 'course') {
    return `I see you're working on ${course}. How can I help?`
  }
  return "Hi! I'm your AI study assistant. Ask me about concepts you're studying, "
    + 'your courses, or what is due. How can I help?'
}

export function noticeText(names: TutorNames): string {
  return names.courseName ? `You're now in ${names.courseName}` : "You're now in a different course"
}
