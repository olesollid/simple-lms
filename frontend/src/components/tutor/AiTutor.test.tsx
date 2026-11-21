import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import AiTutor from './AiTutor'

vi.mock('./TutorWidget', () => ({
  default: () => {
    throw new Error('widget exploded')
  },
}))

describe('AiTutor', () => {
  it('keeps the rest of the page working when the widget crashes', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined)
    render(
      <MemoryRouter>
        <p>The page</p>
        <AiTutor />
      </MemoryRouter>,
    )

    // The widget loads lazily, so wait for it to fail and be contained.
    await vi.waitFor(() => expect(console.error).toHaveBeenCalled())
    expect(screen.getByText('The page')).toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })
})
