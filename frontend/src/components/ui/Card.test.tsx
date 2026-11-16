import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import Card from './Card'

describe('Card', () => {
  it('renders title, description, children and actions', () => {
    render(
      <Card title="Module 1" description="Intro" actions={<button>Open</button>}>
        <p>Body text</p>
      </Card>,
    )

    expect(screen.getByRole('article', { name: 'Module 1' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 2, name: 'Module 1' })).toBeInTheDocument()
    expect(screen.getByText('Intro')).toBeInTheDocument()
    expect(screen.getByText('Body text')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Open' })).toBeInTheDocument()
  })

  it('uses the requested heading level', () => {
    render(<Card title="Nested" headingLevel={3} />)

    expect(screen.getByRole('heading', { level: 3, name: 'Nested' })).toBeInTheDocument()
  })

  it('works with only children', () => {
    render(<Card>Just content</Card>)

    expect(screen.getByText('Just content')).toBeInTheDocument()
    expect(screen.queryByRole('heading')).not.toBeInTheDocument()
  })
})
