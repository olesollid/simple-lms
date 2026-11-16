import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import ErrorMessage from './ErrorMessage'

describe('ErrorMessage', () => {
  it('shows the message as an alert', () => {
    render(<ErrorMessage message="Something went wrong." />)

    expect(screen.getByRole('alert')).toHaveTextContent('Something went wrong.')
  })

  it('shows an optional title and follow-up content', () => {
    render(
      <ErrorMessage title="Could not save" message="Try again later.">
        <button>Retry</button>
      </ErrorMessage>,
    )

    expect(screen.getByRole('alert')).toHaveTextContent('Could not save')
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument()
  })
})
