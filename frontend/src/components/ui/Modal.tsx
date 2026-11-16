import { useEffect, useId, useRef } from 'react'
import type { ComponentPropsWithoutRef } from 'react'
import Button from './Button'
import './ui.css'

export interface ModalProps extends Omit<ComponentPropsWithoutRef<'dialog'>, 'open' | 'title' | 'onClose'> {
  /** Whether the dialog is shown. The parent owns this state. */
  open: boolean
  /** Called when the user asks to close (close button, Escape or backdrop click). */
  onClose: () => void
  /** Optional heading. Without one, pass `aria-label` to name the dialog. */
  title?: string
}

export default function Modal({ open, onClose, title, className, children, ...rest }: ModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const titleId = useId()

  // The native <dialog> keeps its own open state, so sync it with the `open` prop.
  // showModal() gives us the focus trap, inert background, Escape key and backdrop.
  useEffect(() => {
    const dialog = dialogRef.current
    if (!dialog) return
    if (open && !dialog.open) dialog.showModal()
    if (!open && dialog.open) dialog.close()
  }, [open])

  return (
    <dialog
      {...rest}
      ref={dialogRef}
      className={['ui-modal', className].filter(Boolean).join(' ')}
      aria-labelledby={title ? titleId : undefined}
      // Escape (and dialog.close()) fire "close"; only report it if the parent still thinks we are open.
      onClose={() => { if (open) onClose() }}
      // A click on the dialog element itself (not its content) is a click on the backdrop.
      onClick={event => { if (event.target === event.currentTarget) onClose() }}
    >
      {open && (
        <div className="ui-modal-panel">
          <div className="ui-modal-header">
            {title && <h2 id={titleId} className="ui-modal-title">{title}</h2>}
            <Button variant="secondary" className="ui-modal-close" onClick={onClose} aria-label="Close dialog">
              <span aria-hidden="true">✕</span>
            </Button>
          </div>
          <div className="ui-modal-body">{children}</div>
        </div>
      )}
    </dialog>
  )
}
