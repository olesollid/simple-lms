# UI components

Generic, presentation-only building blocks. They know nothing about courses,
modules or assignments; feature components in `src/components/` compose them.
Styles live in `ui.css` (class names start with `ui-`) and use the design tokens
defined in `src/index.css`.

```tsx
import { Button, Card, ErrorMessage, Input, LoadingIndicator, Modal } from './components/ui'
```

**Button**: `variant` is `primary` (default), `secondary` or `danger`. `loading` shows a spinner and disables the button. `type` defaults to `"button"`.
```tsx
<Button onClick={save} loading={saving}>Save</Button>
<Button variant="danger" type="submit">Delete</Button>
```

**Input**: `label` is required. Add `error` and `hint` for messages. It accepts every normal `<input>` prop, so it works controlled (`value` + `onChange`) or uncontrolled (`defaultValue` / `ref`).
```tsx
<Input label="Title" required value={title} onChange={e => setTitle(e.target.value)}
  error={title ? undefined : 'Title is required'} />
```

**Modal**: you own the `open` state. `onClose` is called by the close button, Escape and a backdrop click. Without a `title`, pass `aria-label`.
```tsx
<Modal open={open} onClose={() => setOpen(false)} title="Delete course?">
  <p>This cannot be undone.</p>
</Modal>
```

**Card**: optional `title`, `description`, `actions` (footer) and children. `headingLevel` (2, 3 or 4) keeps the page outline correct.
```tsx
<Card title="Module 1" description="Introduction" actions={<Button>Open</Button>}>
  <p>3 assignments</p>
</Card>
```

**LoadingIndicator**: `<LoadingIndicator message="Loading modules…" />` (role `status`).

**ErrorMessage**: `<ErrorMessage title="Could not save" message="Please try again." />` (role `alert`). Children can hold a retry button. Pass user-friendly text, not raw exceptions.
