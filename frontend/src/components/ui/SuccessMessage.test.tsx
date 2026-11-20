import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import SuccessMessage from './SuccessMessage'

describe('SuccessMessage', () => {
  it('shows the message as a status', () => {
    render(<SuccessMessage message="Saved." />)

    expect(screen.getByRole('status')).toHaveTextContent('Saved.')
  })
})
