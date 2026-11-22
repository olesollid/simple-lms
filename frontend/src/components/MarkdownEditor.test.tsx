import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import MarkdownEditor from './MarkdownEditor'

function Harness({ initial = '' }: { initial?: string }) {
  const [value, setValue] = useState(initial)
  return (
    <>
      <label htmlFor="body">Body</label>
      <MarkdownEditor id="body" value={value} onChange={setValue} />
    </>
  )
}

describe('MarkdownEditor', () => {
  it('wraps the selected text in bold markers', async () => {
    const user = userEvent.setup()
    render(<Harness initial="make this bold" />)
    const area = screen.getByLabelText('Body') as HTMLTextAreaElement
    area.setSelectionRange(5, 9)

    await user.click(screen.getByRole('button', { name: 'Bold' }))

    expect(area.value).toBe('make **this** bold')
  })

  it('inserts an image template with a placeholder description', async () => {
    const user = userEvent.setup()
    render(<Harness />)

    await user.click(screen.getByRole('button', { name: 'Embed image' }))

    expect((screen.getByLabelText('Body') as HTMLTextAreaElement).value).toBe('![description](https://)')
  })
})
