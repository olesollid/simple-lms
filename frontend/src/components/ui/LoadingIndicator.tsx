import './ui.css'

export interface LoadingIndicatorProps {
  /** Text shown next to the spinner and read by screen readers. */
  message?: string
  className?: string
}

export default function LoadingIndicator({ message = 'Loading…', className }: LoadingIndicatorProps) {
  return (
    <div className={['ui-loading', className].filter(Boolean).join(' ')} role="status">
      <span className="ui-spinner" aria-hidden="true" />
      <p>{message}</p>
    </div>
  )
}
