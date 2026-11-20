import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AppLayout from './AppLayout'

const getCurrentUser = vi.hoisted(() => vi.fn())
const logout = vi.hoisted(() => vi.fn())
vi.mock('../services', () => ({ users: { getCurrentUser } }))
vi.mock('../services/auth', () => ({ logout }))

function renderLayout() {
  return render(
    <MemoryRouter initialEntries={['/courses']}>
      <Routes>
        <Route element={<AppLayout />}>
          <Route path="courses" element={<h1>Courses</h1>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )
}

describe('AppHeader', () => {
  beforeEach(() => {
    getCurrentUser.mockReset()
    logout.mockReset()
    logout.mockResolvedValue(undefined)
  })

  it("shows the signed-in user's email and a sign out button", async () => {
    getCurrentUser.mockResolvedValue({ email: 'teacher@example.com', name: 'Teacher' })
    renderLayout()

    expect(await screen.findByText('teacher@example.com')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Sign out' })).toBeInTheDocument()
  })

  it('signs out when the button is clicked', async () => {
    const user = userEvent.setup()
    getCurrentUser.mockResolvedValue({ email: 'teacher@example.com', name: 'Teacher' })
    renderLayout()

    await user.click(await screen.findByRole('button', { name: 'Sign out' }))

    expect(logout).toHaveBeenCalledTimes(1)
  })

  it('shows sign-in links when not signed in', async () => {
    getCurrentUser.mockRejectedValue(new Error('not authenticated'))
    renderLayout()

    const githubLink = await screen.findByRole('link', { name: 'Sign in with GitHub' })
    expect(githubLink).toHaveAttribute('href', '/oauth2/authorization/github')
    expect(screen.getByRole('link', { name: 'Sign in with Google' }))
      .toHaveAttribute('href', '/oauth2/authorization/google')
    expect(screen.queryByRole('button', { name: 'Sign out' })).not.toBeInTheDocument()
  })
})
