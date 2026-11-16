import type { ComponentPropsWithRef } from 'react'
import './ui.css'

export type ButtonVariant = 'primary' | 'secondary' | 'danger'

export interface ButtonProps extends ComponentPropsWithRef<'button'> {
  /** Visual style. Defaults to `primary`. */
  variant?: ButtonVariant
  /** Shows a spinner and disables the button while an action is running. */
  loading?: boolean
}

export default function Button({
  variant = 'primary',
  loading = false,
  type = 'button', // HTML's default is "submit", which surprises people inside forms.
  disabled,
  className,
  children,
  ...rest
}: ButtonProps) {
  const classes = ['ui-button', `ui-button--${variant}`, className].filter(Boolean).join(' ')

  return (
    <button
      {...rest}
      type={type}
      className={classes}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
    >
      {loading && <span className="ui-spinner ui-spinner--small" aria-hidden="true" />}
      {children}
    </button>
  )
}
