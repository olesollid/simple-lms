import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import App from './App'

vi.mock('./services', () => ({ courses: { getCourses: () => Promise.resolve([]) } }))

describe('App', () => {
  it('renders the courses page as the application root', async () => {
    render(<App />)

    expect(screen.getByRole('heading', { level: 1, name: 'Courses' })).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: 'No courses yet' })).toBeInTheDocument()
  })
})
