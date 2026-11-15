import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import App from './App'
import { MemoryRouter } from 'react-router'
import userEvent from '@testing-library/user-event'

vi.mock('./services', () => ({ courses: { getCourses: () => Promise.resolve([]) } }))

describe('App', () => {
  it('opens the dashboard by default and navigates through the shared layout', async () => {
    const user = userEvent.setup()
    render(<MemoryRouter><App /></MemoryRouter>)

    expect(screen.getByRole('heading', { level: 1, name: 'Dashboard' })).toBeInTheDocument()
    await user.click(screen.getByRole('link', { name: 'Courses' }))

    expect(screen.getByRole('heading', { level: 1, name: 'Courses' })).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: 'No courses yet' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Courses' })).toHaveAttribute('aria-current', 'page')
    await user.click(screen.getByRole('link', { name: 'Profile' }))
    expect(screen.getByRole('heading', { level: 1, name: 'Profile' })).toBeInTheDocument()
    expect(screen.getAllByRole('banner')).toHaveLength(1)
  })

  it('supports direct routes and closes the mobile menu after navigation', async () => {
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/profile']}><App /></MemoryRouter>)
    expect(screen.getByRole('heading', { level: 1, name: 'Profile' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Open menu' }))
    expect(screen.getByRole('button', { name: 'Close menu' })).toHaveAttribute('aria-expanded', 'true')
    await user.click(screen.getByRole('link', { name: 'Dashboard' }))
    expect(screen.getByRole('button', { name: 'Open menu' })).toHaveAttribute('aria-expanded', 'false')
    expect(screen.getByRole('main')).toHaveFocus()
  })

  it('shows a fallback for unknown routes', () => {
    render(<MemoryRouter initialEntries={['/missing']}><App /></MemoryRouter>)
    expect(screen.getByRole('heading', { name: 'Page not found' })).toBeInTheDocument()
  })
})
