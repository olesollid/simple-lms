import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import Input from './Input'

describe('Input', () => {
  it('associates the label with the input', () => {
    render(<Input label="Email" type="email" placeholder="you@example.com" />)

    const input = screen.getByLabelText('Email')
    expect(input).toHaveAttribute('type', 'email')
    expect(input).toHaveAttribute('placeholder', 'you@example.com')
  })

  it('supports required and disabled', () => {
    render(<Input label="Name" required disabled />)

    expect(screen.getByLabelText(/Name/)).toBeRequired()
    expect(screen.getByLabelText(/Name/)).toBeDisabled()
  })

  it('links the error message and marks the input invalid', () => {
    render(<Input label="Name" error="Name is required" />)

    const input = screen.getByLabelText('Name')
    expect(input).toBeInvalid()
    expect(input).toHaveAccessibleDescription('Name is required')
  })

  it('works as a controlled input', async () => {
    function Controlled() {
      const [value, setValue] = useState('')
      return <Input label="Title" value={value} onChange={event => setValue(event.target.value)} />
    }
    render(<Controlled />)

    await userEvent.type(screen.getByLabelText('Title'), 'Hi')

    expect(screen.getByLabelText('Title')).toHaveValue('Hi')
  })
})
