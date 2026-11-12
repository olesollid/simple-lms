import type { CourseResponse } from '../types/course'

export default function CourseCard({ course }: { course: CourseResponse }) {
  const semester = `${course.term === 'AUTUMN' ? 'Autumn' : 'Spring'} ${course.year}`

  return (
    <article className="course-card" aria-labelledby={`course-${course.id}`}>
      <p className="course-semester">{semester}</p>
      <h2 id={`course-${course.id}`}>{course.title}</h2>
      <p className="course-description">{course.description.trim() || 'No description provided.'}</p>
    </article>
  )
}
