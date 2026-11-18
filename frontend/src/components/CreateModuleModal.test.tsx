import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../services/apiClient'
import CreateModuleModal from './CreateModuleModal'

const createModule = vi.hoisted(() => vi.fn())
vi.mock('../services', () => ({ modules: { createModule } }))

describe('CreateModuleModal', () => {
  beforeEach(() => {
    createModule.mockReset()
  })

  it('creates a module with the entered title', async () => {
    const user = userEvent.setup()
    const onCreated = vi.fn()
    const created = { id: 1, courseId: 1, title: 'Week 1', dependencyIds: [] }
    createModule.mockResolvedValue(created)

    render(<CreateModuleModal courseId={1} open={true} onClose={vi.fn()} onCreated={onCreated} />)

    await user.type(screen.getByLabelText(/Title/), 'Week 1')
    await user.click(screen.getByRole('button', { name: 'Create module' }))

    expect(createModule).toHaveBeenCalledWith(1, { title: 'Week 1' }, {})
    expect(onCreated).toHaveBeenCalledWith(created)
  })

  it('shows an error when creation fails', async () => {
    const user = userEvent.setup()
    createModule.mockRejectedValue(new ApiError(403, 'not a teacher', null))

    render(<CreateModuleModal courseId={1} open={true} onClose={vi.fn()} onCreated={vi.fn()} />)
    await user.type(screen.getByLabelText(/Title/), 'Week 1')
    await user.click(screen.getByRole('button', { name: 'Create module' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('not a teacher')
  })
})
