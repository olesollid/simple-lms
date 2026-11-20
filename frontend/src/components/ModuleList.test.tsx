import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../services/apiClient'
import ModuleList from './ModuleList'

const getContentsByModule = vi.hoisted(() => vi.fn())
const deleteModule = vi.hoisted(() => vi.fn())
vi.mock('../services', () => ({ contents: { getContentsByModule }, modules: { deleteModule } }))

const make = (id: number) => ({ id, courseId: 1, title: `Module ${id}`, dependencyIds: [], locked: false })

function renderWithRouter(ui: React.ReactElement) {
  return render(<MemoryRouter>{ui}</MemoryRouter>)
}

describe('ModuleList', () => {
  beforeEach(() => {
    getContentsByModule.mockReset()
    getContentsByModule.mockResolvedValue([])
    deleteModule.mockReset()
  })

  it('shows an empty state', () => {
    renderWithRouter(<ModuleList modules={[]} isTeacher={false} onModuleDeleted={vi.fn()} />)

    expect(screen.getByRole('heading', { name: 'No modules yet' })).toBeInTheDocument()
  })

  it('lists modules', () => {
    renderWithRouter(
      <ModuleList modules={[make(1), make(2)]} isTeacher={false} onModuleDeleted={vi.fn()} />,
    )

    expect(screen.getAllByRole('listitem')).toHaveLength(2)
    expect(screen.getByRole('heading', { name: 'Module 1' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Module 2' })).toBeInTheDocument()
  })

  it('shows an "Add content" button for a teacher', () => {
    renderWithRouter(<ModuleList modules={[make(1)]} isTeacher={true} onModuleDeleted={vi.fn()} />)

    expect(screen.getByRole('button', { name: 'Add content' })).toBeInTheDocument()
  })

  it('does not show an "Add content" button for a student', () => {
    renderWithRouter(<ModuleList modules={[make(1)]} isTeacher={false} onModuleDeleted={vi.fn()} />)

    expect(screen.queryByRole('button', { name: 'Add content' })).not.toBeInTheDocument()
  })

  it('does not show a "Delete module" button for a student', () => {
    renderWithRouter(<ModuleList modules={[make(1)]} isTeacher={false} onModuleDeleted={vi.fn()} />)

    expect(screen.queryByRole('button', { name: 'Delete module' })).not.toBeInTheDocument()
  })

  it('shows a locked module as locked instead of loading its content', () => {
    renderWithRouter(
      <ModuleList modules={[{ ...make(1), locked: true }]} isTeacher={false} onModuleDeleted={vi.fn()} />,
    )

    expect(screen.getByText('Locked')).toBeInTheDocument()
    expect(screen.getByText(/complete the prerequisite modules/i)).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Module 1' })).toBeInTheDocument()
    expect(getContentsByModule).not.toHaveBeenCalled()
  })

  it('loads content and shows no lock badge for an unlocked module', async () => {
    renderWithRouter(<ModuleList modules={[make(1)]} isTeacher={false} onModuleDeleted={vi.fn()} />)

    expect(await screen.findByText('No content yet.')).toBeInTheDocument()
    expect(screen.queryByText('Locked')).not.toBeInTheDocument()
    expect(getContentsByModule).toHaveBeenCalledWith(1, expect.anything())
  })

  describe('as a teacher', () => {
    const confirmSpy = vi.spyOn(window, 'confirm')

    afterEach(() => confirmSpy.mockReset())

    it('deletes a module after confirming', async () => {
      const user = userEvent.setup()
      const onModuleDeleted = vi.fn()
      confirmSpy.mockReturnValue(true)
      deleteModule.mockResolvedValue({ deletedModuleId: 1, affectedModuleIds: [] })
      renderWithRouter(<ModuleList modules={[make(1)]} isTeacher={true} onModuleDeleted={onModuleDeleted} />)

      await user.click(screen.getByRole('button', { name: 'Delete module' }))

      expect(deleteModule).toHaveBeenCalledWith(1, {})
      expect(onModuleDeleted).toHaveBeenCalled()
    })

    it('does not delete when the confirmation is declined', async () => {
      const user = userEvent.setup()
      confirmSpy.mockReturnValue(false)
      renderWithRouter(<ModuleList modules={[make(1)]} isTeacher={true} onModuleDeleted={vi.fn()} />)

      await user.click(screen.getByRole('button', { name: 'Delete module' }))

      expect(deleteModule).not.toHaveBeenCalled()
    })

    it('shows an error when deletion fails', async () => {
      const user = userEvent.setup()
      confirmSpy.mockReturnValue(true)
      deleteModule.mockRejectedValue(new ApiError(403, 'Forbidden', null))
      renderWithRouter(<ModuleList modules={[make(1)]} isTeacher={true} onModuleDeleted={vi.fn()} />)

      await user.click(screen.getByRole('button', { name: 'Delete module' }))

      expect(await screen.findByRole('alert')).toHaveTextContent('Forbidden')
    })
  })
})
