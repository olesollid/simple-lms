import { useRef } from 'react'

type Action = { label: string; hint: string; before: string; after: string; placeholder: string; select?: string }

// `select` is the part of the inserted text left selected, so the teacher can type over it.
const ACTIONS: Action[] = [
  { label: 'B', hint: 'Bold', before: '**', after: '**', placeholder: 'bold text' },
  { label: 'I', hint: 'Italic', before: '_', after: '_', placeholder: 'italic text' },
  { label: 'H', hint: 'Heading', before: '## ', after: '', placeholder: 'Heading' },
  { label: '•', hint: 'Bulleted list', before: '- ', after: '', placeholder: 'List item' },
  { label: '</>', hint: 'Code', before: '`', after: '`', placeholder: 'code' },
  { label: 'Link', hint: 'Link', before: '[', after: '](https://)', placeholder: 'link text', select: 'https://' },
  { label: 'Image', hint: 'Embed image', before: '![', after: '](https://)', placeholder: 'description', select: 'https://' },
]

/** A textarea with Markdown formatting buttons; pages render the text as Markdown. */
export default function MarkdownEditor({ id, value, onChange, rows = 6, required }: {
  id: string
  value: string
  onChange: (value: string) => void
  rows?: number
  required?: boolean
}) {
  const ref = useRef<HTMLTextAreaElement>(null)

  function apply(action: Action) {
    const area = ref.current
    if (!area) return
    const { selectionStart: start, selectionEnd: end } = area
    const text = value.slice(start, end) || action.placeholder
    const inserted = action.before + text + action.after
    onChange(value.slice(0, start) + inserted + value.slice(end))
    // Restore focus and select what the teacher most likely wants to replace next.
    const from = action.select
      ? start + action.before.length + text.length + action.after.indexOf(action.select)
      : start + action.before.length
    const to = action.select ? from + action.select.length : from + text.length
    requestAnimationFrame(() => {
      area.focus()
      area.setSelectionRange(from, to)
    })
  }

  return (
    <div className="markdown-editor">
      <div className="markdown-toolbar" role="toolbar" aria-label="Formatting">
        {ACTIONS.map(action => (
          <button key={action.hint} type="button" className="markdown-toolbar-button"
            aria-label={action.hint} title={action.hint} onClick={() => apply(action)}>
            {action.label}
          </button>
        ))}
      </div>
      <textarea ref={ref} id={id} className="ui-input" rows={rows} value={value}
        onChange={event => onChange(event.target.value)} required={required} />
    </div>
  )
}
