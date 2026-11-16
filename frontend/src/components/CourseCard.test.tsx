import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { MemoryRouter } from 'react-router'
import CourseCard from './CourseCard'

const course = { id: 1, title: 'DAT250', description: 'Software technology', term: 'AUTUMN' as const, year: 2026 }

describe('CourseCard', () => {
  it('shows title, semester and description', () => {
    render(<MemoryRouter><CourseCard course={course} /></MemoryRouter>)

    expect(screen.getByRole('heading', { name: 'DAT250' })).toBeInTheDocument()
    expect(screen.getByText('Autumn 2026')).toBeInTheDocument()
    expect(screen.getByText('Software technology')).toBeInTheDocument()
  })

  it('labels spring courses', () => {
    render(<MemoryRouter><CourseCard course={{ ...course, term: 'SPRING' }} /></MemoryRouter>)

    expect(screen.getByText('Spring 2026')).toBeInTheDocument()
  })

  it('shows a fallback for a blank description', () => {
    render(<MemoryRouter><CourseCard course={{ ...course, description: '   ' }} /></MemoryRouter>)

    expect(screen.getByText('No description provided.')).toBeInTheDocument()
  })

  it('links to the course detail page', () => {
    render(<MemoryRouter><CourseCard course={course} /></MemoryRouter>)

    expect(screen.getByRole('link', { name: 'DAT250' })).toHaveAttribute('href', '/courses/1')
  })
})
