import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import Button from './Button'

describe('Button', () => {
  it('renders its children and defaults to type="button"', () => {
    render(<Button>Save</Button>)

    expect(screen.getByRole('button', { name: 'Save' })).toHaveAttribute('type', 'button')
  })

  it('applies the variant class', () => {
    render(<Button variant="danger">Delete</Button>)

    expect(screen.getByRole('button')).toHaveClass('ui-button--danger')
  })

  it('calls onClick', async () => {
    const onClick = vi.fn()
    render(<Button onClick={onClick}>Go</Button>)

    await userEvent.click(screen.getByRole('button'))

    expect(onClick).toHaveBeenCalledOnce()
  })

  it('does not call onClick when disabled', async () => {
    const onClick = vi.fn()
    render(<Button disabled onClick={onClick}>Go</Button>)

    await userEvent.click(screen.getByRole('button'))

    expect(screen.getByRole('button')).toBeDisabled()
    expect(onClick).not.toHaveBeenCalled()
  })

  it('is disabled and marked busy while loading', () => {
    render(<Button loading>Save</Button>)

    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled()
    expect(screen.getByRole('button')).toHaveAttribute('aria-busy', 'true')
  })
})
