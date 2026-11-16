import type { ReactNode } from 'react'
import './ui.css'

export interface ErrorMessageProps {
  /** A user-friendly explanation. Never pass stack traces or raw exception text. */
  message: string
  title?: string
  /** Optional follow-up, such as a "Try again" button. */
  children?: ReactNode
  className?: string
}

export default function ErrorMessage({ message, title, children, className }: ErrorMessageProps) {
  return (
    <div className={['ui-error', className].filter(Boolean).join(' ')} role="alert">
      <span className="ui-error-icon" aria-hidden="true">!</span>
      <div>
        {title && <p className="ui-error-title">{title}</p>}
        <p>{message}</p>
        {children && <div className="ui-error-actions">{children}</div>}
      </div>
    </div>
  )
}
