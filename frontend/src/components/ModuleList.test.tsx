import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../services/apiClient'
import ModuleList from './ModuleList'

const getContentsByModule = vi.hoisted(() => vi.fn())
const deleteModule = vi.hoisted(() => vi.fn())
const addDependency = vi.hoisted(() => vi.fn())
const removeDependency = vi.hoisted(() => vi.fn())
const reorderModules = vi.hoisted(() => vi.fn())
vi.mock('../services', () => ({
  contents: { getContentsByModule },
  modules: { deleteModule, addDependency, removeDependency, reorder: reorderModules },
}))

const make = (id: number) => ({ id, courseId: 1, title: `Module ${id}`, dependencyIds: [], locked: false })

function renderWithRouter(ui: React.ReactElement) {
  return render(<MemoryRouter>{ui}</MemoryRouter>)
}

describe('ModuleList', () => {
  beforeEach(() => {
    getContentsByModule.mockReset()
    getContentsByModule.mockResolvedValue([])
    deleteModule.mockReset()
    addDependency.mockReset().mockResolvedValue({})
    removeDependency.mockReset().mockResolvedValue(undefined)
    reorderModules.mockReset().mockResolvedValue([])
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
    expect(screen.getByText(/to unlock this module/i)).toBeInTheDocument()
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

  it('tells a student which modules to complete to unlock a locked module', () => {
    renderWithRouter(
      <ModuleList
        modules={[{ ...make(1), title: 'Week 1' }, { ...make(2), title: 'Week 2', dependencyIds: [1], locked: true }]}
        isTeacher={false}
        onModuleDeleted={vi.fn()}
      />,
    )

    expect(screen.getByText('Complete every item in Week 1 to unlock this module.')).toBeInTheDocument()
  })

  it('shows a student their progress and which items are completed', async () => {
    getContentsByModule.mockResolvedValue([
      { id: 10, moduleId: 1, type: 'StaticPage', title: 'Intro', position: 0 },
      { id: 11, moduleId: 1, type: 'Quiz', title: 'Check', position: 1 },
    ])
    renderWithRouter(
      <ModuleList modules={[make(1), { ...make(2), dependencyIds: [1], locked: true }]} isTeacher={false}
        completedContentIds={[10]} onModuleDeleted={vi.fn()} />,
    )

    expect(await screen.findByText('1 of 2 completed')).toBeInTheDocument()
    expect(screen.getByText('Intro').closest('li')).toHaveTextContent('✓ Completed')
    expect(screen.getByText('Check').closest('li')).not.toHaveTextContent('Completed')
  })

  it('shows teachers no progress, but lets them add a prerequisite', async () => {
    getContentsByModule.mockResolvedValue([{ id: 10, moduleId: 2, type: 'StaticPage', title: 'Intro', position: 0 }])
    const onModulesChanged = vi.fn()
    const user = userEvent.setup()
    renderWithRouter(
      <ModuleList
        modules={[{ ...make(1), title: 'Week 1' }, { ...make(2), title: 'Week 2' }]}
        isTeacher={true}
        onModuleDeleted={vi.fn()}
        onModulesChanged={onModulesChanged}
      />,
    )

    expect(screen.queryByText(/completed/)).not.toBeInTheDocument()
    await user.selectOptions(screen.getByLabelText('Add prerequisite to Week 2'), 'Week 1')
    const week2 = screen.getByLabelText('Add prerequisite to Week 2').closest('.module-prerequisites')!
    await user.click(within(week2 as HTMLElement).getByRole('button', { name: 'Add' }))

    expect(addDependency).toHaveBeenCalledWith(2, 1, {})
    expect(onModulesChanged).toHaveBeenCalled()
  })

  it('lets a teacher remove a prerequisite and shows why a change was refused', async () => {
    removeDependency.mockRejectedValueOnce(new ApiError(400, 'That would create a cycle', null))
    const user = userEvent.setup()
    renderWithRouter(
      <ModuleList
        modules={[{ ...make(1), title: 'Week 1' }, { ...make(2), title: 'Week 2', dependencyIds: [1] }]}
        isTeacher={true}
        onModuleDeleted={vi.fn()}
        onModulesChanged={vi.fn()}
      />,
    )

    await user.click(screen.getByRole('button', { name: 'Remove prerequisite Week 1' }))

    expect(removeDependency).toHaveBeenCalledWith(2, 1, {})
    expect(await screen.findByText('That would create a cycle')).toBeInTheDocument()
  })

  it('lets a teacher move a module down', async () => {
    const onModulesChanged = vi.fn()
    const user = userEvent.setup()
    renderWithRouter(
      <ModuleList modules={[make(1), make(2), make(3)]} isTeacher={true}
        onModuleDeleted={vi.fn()} onModulesChanged={onModulesChanged} />,
    )

    expect(screen.getByRole('button', { name: 'Move Module 1 up' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Move Module 1 down' }))

    expect(reorderModules).toHaveBeenCalledWith(1, [2, 1, 3], {})
    expect(onModulesChanged).toHaveBeenCalled()
  })

  it('explains why a module cannot move above its prerequisite, without reloading', async () => {
    reorderModules.mockRejectedValue(new ApiError(400,
      'Week 3 must stay below Week 2, because Week 2 is a prerequisite of Week 3.', null))
    const onModulesChanged = vi.fn()
    const user = userEvent.setup()
    renderWithRouter(
      <ModuleList
        modules={[
          { ...make(1), title: 'Week 1' },
          { ...make(2), title: 'Week 2' },
          { ...make(3), title: 'Week 3', dependencyIds: [1, 2] },
        ]}
        isTeacher={true}
        onModuleDeleted={vi.fn()}
        onModulesChanged={onModulesChanged}
      />,
    )

    await user.click(screen.getByRole('button', { name: 'Move Week 3 up' }))

    expect(await screen.findByText(
      'Week 3 must stay below Week 2, because Week 2 is a prerequisite of Week 3.')).toBeInTheDocument()
    expect(onModulesChanged).not.toHaveBeenCalled()
  })

  it('shows no progress count for a module that does not unlock anything, but keeps the checkmarks', async () => {
    getContentsByModule.mockResolvedValue([
      { id: 10, moduleId: 1, type: 'Quiz', title: 'Check', position: 0 },
      { id: 11, moduleId: 1, type: 'StaticPage', title: 'Intro', position: 1 },
    ])
    renderWithRouter(
      <ModuleList modules={[make(1)]} isTeacher={false} completedContentIds={[10]} onModuleDeleted={vi.fn()} />,
    )

    expect((await screen.findByText('Check')).closest('li')).toHaveTextContent('✓ Completed')
    expect(screen.queryByText(/of 2 completed/)).not.toBeInTheDocument()
  })
})
