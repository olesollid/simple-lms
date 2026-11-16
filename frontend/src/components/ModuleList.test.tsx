import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import ModuleList from './ModuleList'

const make = (id: number) => ({ id, courseId: 1, title: `Module ${id}`, dependencyIds: [] })

describe('ModuleList', () => {
  it('shows an empty state', () => {
    render(<ModuleList modules={[]} />)

    expect(screen.getByRole('heading', { name: 'No modules yet' })).toBeInTheDocument()
  })

  it('lists modules', () => {
    render(<ModuleList modules={[make(1), make(2)]} />)

    expect(screen.getAllByRole('listitem')).toHaveLength(2)
    expect(screen.getByRole('heading', { name: 'Module 1' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Module 2' })).toBeInTheDocument()
  })
})
