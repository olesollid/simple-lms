import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import Modal from './Modal'

describe('Modal', () => {
  it('shows title and content when open', () => {
    render(<Modal open onClose={() => {}} title="Confirm">Are you sure?</Modal>)

    expect(screen.getByRole('dialog', { name: 'Confirm' })).toBeInTheDocument()
    expect(screen.getByText('Are you sure?')).toBeInTheDocument()
  })

  it('renders nothing visible when closed', () => {
    render(<Modal open={false} onClose={() => {}} title="Confirm">Are you sure?</Modal>)

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.queryByText('Are you sure?')).not.toBeInTheDocument()
  })

  it('calls onClose from the close button', async () => {
    const onClose = vi.fn()
    render(<Modal open onClose={onClose} title="Confirm">Body</Modal>)

    await userEvent.click(screen.getByRole('button', { name: 'Close dialog' }))

    expect(onClose).toHaveBeenCalledOnce()
  })

  it('calls onClose when the dialog is dismissed with Escape', () => {
    const onClose = vi.fn()
    render(<Modal open onClose={onClose} title="Confirm">Body</Modal>)

    // The browser turns Escape into dialog.close(), which fires "close".
    screen.getByRole('dialog').dispatchEvent(new Event('close'))

    expect(onClose).toHaveBeenCalledOnce()
  })

  it('opens and closes under parent control', async () => {
    function Example() {
      const [open, setOpen] = useState(false)
      return (
        <>
          <button onClick={() => setOpen(true)}>Open</button>
          <Modal open={open} onClose={() => setOpen(false)} title="Hello">Content</Modal>
        </>
      )
    }
    render(<Example />)

    await userEvent.click(screen.getByRole('button', { name: 'Open' }))
    expect(screen.getByRole('dialog', { name: 'Hello' })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Close dialog' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})
