import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import CourseCard from './CourseCard'

const course = { id: 1, title: 'DAT250', description: 'Software technology', term: 'AUTUMN' as const, year: 2026 }

describe('CourseCard', () => {
  it('shows title, semester and description', () => {
    render(<CourseCard course={course} />)

    expect(screen.getByRole('heading', { name: 'DAT250' })).toBeInTheDocument()
    expect(screen.getByText('Autumn 2026')).toBeInTheDocument()
    expect(screen.getByText('Software technology')).toBeInTheDocument()
  })

  it('labels spring courses', () => {
    render(<CourseCard course={{ ...course, term: 'SPRING' }} />)

    expect(screen.getByText('Spring 2026')).toBeInTheDocument()
  })

  it('shows a fallback for a blank description', () => {
    render(<CourseCard course={{ ...course, description: '   ' }} />)

    expect(screen.getByText('No description provided.')).toBeInTheDocument()
  })
})
