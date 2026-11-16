import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { MemoryRouter } from 'react-router'
import CourseList from './CourseList'

const make = (id: number) => ({ id, title: `Course ${id}`, description: 'd', term: 'SPRING' as const, year: 2026 })

describe('CourseList', () => {
  it('shows a loading status', () => {
    render(<MemoryRouter><CourseList state={{ status: 'loading' }} /></MemoryRouter>)

    expect(screen.getByRole('status')).toHaveTextContent('Loading courses')
  })

  it('shows the error message as an alert', () => {
    render(<MemoryRouter><CourseList state={{ status: 'error', message: 'Nope' }} /></MemoryRouter>)

    expect(screen.getByRole('alert')).toHaveTextContent('Nope')
  })

  it('shows an empty state', () => {
    render(<MemoryRouter><CourseList state={{ status: 'success', courses: [] }} /></MemoryRouter>)

    expect(screen.getByRole('heading', { name: 'No courses yet' })).toBeInTheDocument()
  })

  it('lists courses with a count', () => {
    render(<MemoryRouter><CourseList state={{ status: 'success', courses: [make(1), make(2)] }} /></MemoryRouter>)

    expect(screen.getByText('2 courses')).toBeInTheDocument()
    expect(screen.getAllByRole('listitem')).toHaveLength(2)
  })

  it('uses the singular for one course', () => {
    render(<MemoryRouter><CourseList state={{ status: 'success', courses: [make(1)] }} /></MemoryRouter>)

    expect(screen.getByText('1 course')).toBeInTheDocument()
  })
})
