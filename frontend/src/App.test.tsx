import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import App from './App'
import { MemoryRouter } from 'react-router'
import userEvent from '@testing-library/user-event'

const course = { id: 1, title: 'DAT250', description: 'Software technology', term: 'AUTUMN', year: 2026 }

vi.mock('./services', () => ({
  courses: {
    getCourses: () => Promise.resolve([course]),
    getCourse: () => Promise.resolve(course),
  },
  modules: { getModules: () => Promise.resolve([]) },
  contents: { getContentsByModule: () => Promise.resolve([]) },
  users: { getCurrentUser: () => Promise.resolve({ email: 'teacher@example.com', name: 'Teacher' }) },
  enrollments: { getEnrollment: () => Promise.resolve({ role: 'STUDENT' }) },
}))

describe('App', () => {
  it('opens courses by default and navigates through the shared layout', async () => {
    const user = userEvent.setup()
    render(<MemoryRouter><App /></MemoryRouter>)

    expect(screen.getByRole('heading', { level: 1, name: 'Courses' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Dashboard' })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Canvas 2.0' })).toHaveAttribute('href', '/courses')
    expect(await screen.findByRole('heading', { name: /DAT250/ })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Courses' })).toHaveAttribute('aria-current', 'page')
    await user.click(screen.getByRole('link', { name: 'Profile' }))
    expect(screen.getByRole('heading', { level: 1, name: 'Profile' })).toBeInTheDocument()
    expect(screen.getAllByRole('banner')).toHaveLength(1)
  })

  it('navigates from the course list into a course detail page', async () => {
    const user = userEvent.setup()
    render(<MemoryRouter><App /></MemoryRouter>)

    await user.click(screen.getByRole('link', { name: 'Courses' }))
    await user.click(await screen.findByRole('link', { name: 'DAT250' }))

    expect(await screen.findByRole('heading', { level: 1, name: /DAT250/ })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'No modules yet' })).toBeInTheDocument()
  })

  it('supports direct routes and closes the mobile menu after navigation', async () => {
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/profile']}><App /></MemoryRouter>)
    expect(screen.getByRole('heading', { level: 1, name: 'Profile' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Open menu' }))
    expect(screen.getByRole('button', { name: 'Close menu' })).toHaveAttribute('aria-expanded', 'true')
    await user.click(screen.getByRole('link', { name: 'Courses' }))
    expect(screen.getByRole('button', { name: 'Open menu' })).toHaveAttribute('aria-expanded', 'false')
    expect(screen.getByRole('main')).toHaveFocus()
  })

  it('shows a fallback for unknown routes', () => {
    render(<MemoryRouter initialEntries={['/missing']}><App /></MemoryRouter>)
    expect(screen.getByRole('heading', { name: 'Page not found' })).toBeInTheDocument()
  })

  it('redirects old dashboard links to courses', async () => {
    render(<MemoryRouter initialEntries={['/dashboard']}><App /></MemoryRouter>)
    expect(screen.getByRole('heading', { level: 1, name: 'Courses' })).toBeInTheDocument()
    expect(await screen.findByRole('link', { name: 'DAT250' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Courses' })).toHaveAttribute('aria-current', 'page')
  })
})
