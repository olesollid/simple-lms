import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import CourseList from './CourseList'

const make = (id: number) => ({ id, title: `Course ${id}`, description: 'd', term: 'SPRING' as const, year: 2026 })

describe('CourseList', () => {
  it('shows a loading status', () => {
    render(<CourseList state={{ status: 'loading' }} />)

    expect(screen.getByRole('status')).toHaveTextContent('Loading courses')
  })

  it('shows the error message as an alert', () => {
    render(<CourseList state={{ status: 'error', message: 'Nope' }} />)

    expect(screen.getByRole('alert')).toHaveTextContent('Nope')
  })

  it('shows an empty state', () => {
    render(<CourseList state={{ status: 'success', courses: [] }} />)

    expect(screen.getByRole('heading', { name: 'No courses yet' })).toBeInTheDocument()
  })

  it('lists courses with a count', () => {
    render(<CourseList state={{ status: 'success', courses: [make(1), make(2)] }} />)

    expect(screen.getByText('2 courses')).toBeInTheDocument()
    expect(screen.getAllByRole('listitem')).toHaveLength(2)
  })

  it('uses the singular for one course', () => {
    render(<CourseList state={{ status: 'success', courses: [make(1)] }} />)

    expect(screen.getByText('1 course')).toBeInTheDocument()
  })
})
