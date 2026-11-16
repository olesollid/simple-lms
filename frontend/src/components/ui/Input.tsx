import { useId } from 'react'
import type { ComponentPropsWithRef } from 'react'
import './ui.css'

export interface InputProps extends ComponentPropsWithRef<'input'> {
  /** Visible label. Required so that every input has an accessible name. */
  label: string
  /** Validation message. When set, the input is marked invalid. */
  error?: string
  /** Optional help text shown under the input. */
  hint?: string
}

export default function Input({
  label,
  error,
  hint,
  id,
  type = 'text',
  required,
  className,
  ...rest
}: InputProps) {
  const generatedId = useId()
  const inputId = id ?? generatedId
  const hintId = `${inputId}-hint`
  const errorId = `${inputId}-error`
  const describedBy = [hint && hintId, error && errorId].filter(Boolean).join(' ') || undefined

  return (
    <div className={['ui-field', className].filter(Boolean).join(' ')}>
      <label className="ui-label" htmlFor={inputId}>
        {label}
        {required && <span className="ui-required" aria-hidden="true"> *</span>}
      </label>
      <input
        {...rest}
        id={inputId}
        type={type}
        required={required}
        className="ui-input"
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
      />
      {hint && <p id={hintId} className="ui-hint">{hint}</p>}
      {error && <p id={errorId} className="ui-field-error"><span aria-hidden="true">⚠ </span>{error}</p>}
    </div>
  )
}
