import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ModuleList from './ModuleList'

const getContentsByModule = vi.hoisted(() => vi.fn())
vi.mock('../services', () => ({ contents: { getContentsByModule } }))

const make = (id: number) => ({ id, courseId: 1, title: `Module ${id}`, dependencyIds: [] })

function renderWithRouter(ui: React.ReactElement) {
  return render(<MemoryRouter>{ui}</MemoryRouter>)
}

describe('ModuleList', () => {
  beforeEach(() => {
    getContentsByModule.mockReset()
    getContentsByModule.mockResolvedValue([])
  })

  it('shows an empty state', () => {
    renderWithRouter(<ModuleList modules={[]} />)

    expect(screen.getByRole('heading', { name: 'No modules yet' })).toBeInTheDocument()
  })

  it('lists modules', () => {
    renderWithRouter(<ModuleList modules={[make(1), make(2)]} />)

    expect(screen.getAllByRole('listitem')).toHaveLength(2)
    expect(screen.getByRole('heading', { name: 'Module 1' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Module 2' })).toBeInTheDocument()
  })
})
