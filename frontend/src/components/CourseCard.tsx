import { Link } from 'react-router'
import type { CourseResponse } from '../types/course'
import type { Role } from '../types/enrollment'

export default function CourseCard({ course, role }: { course: CourseResponse; role?: Role }) {
  const semester = `${course.term === 'AUTUMN' ? 'Autumn' : 'Spring'} ${course.year}`
  const isTeacher = role === 'TEACHER'

  return (
    <article className="course-card" aria-labelledby={`course-${course.id}`}>
      <p className="course-semester">{semester}</p>
      <h2 id={`course-${course.id}`}>
        <Link to={`/courses/${course.id}`}>{course.title}</Link>
        {role && (
          <span className={`role-badge ${isTeacher ? 'role-badge--teacher' : 'role-badge--student'}`}>
            {isTeacher ? 'Teacher' : 'Student'}
          </span>
        )}
      </h2>
      <p className="course-description">{course.description.trim() || 'No description provided.'}</p>
    </article>
  )
}
