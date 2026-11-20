import './ui.css'

export interface SuccessMessageProps {
  message: string
  className?: string
}

export default function SuccessMessage({ message, className }: SuccessMessageProps) {
  return (
    <div className={['ui-success', className].filter(Boolean).join(' ')} role="status">
      <span className="ui-success-icon" aria-hidden="true">✓</span>
      <p>{message}</p>
    </div>
  )
}
