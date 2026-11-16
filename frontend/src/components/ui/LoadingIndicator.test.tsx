import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import LoadingIndicator from './LoadingIndicator'

describe('LoadingIndicator', () => {
  it('announces a default message as a status', () => {
    render(<LoadingIndicator />)

    expect(screen.getByRole('status')).toHaveTextContent('Loading…')
  })

  it('shows a custom message', () => {
    render(<LoadingIndicator message="Loading modules…" />)

    expect(screen.getByRole('status')).toHaveTextContent('Loading modules…')
  })
})
