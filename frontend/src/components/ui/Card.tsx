import { useId } from 'react'
import type { ComponentPropsWithoutRef, ReactNode } from 'react'
import './ui.css'

export interface CardProps extends Omit<ComponentPropsWithoutRef<'article'>, 'title'> {
  title?: string
  description?: string
  /** Heading level for the title, so the page outline stays correct. Defaults to 2. */
  headingLevel?: 2 | 3 | 4
  /** Content for the footer, typically buttons or links. */
  actions?: ReactNode
}

export default function Card({
  title,
  description,
  headingLevel = 2,
  actions,
  className,
  children,
  ...rest
}: CardProps) {
  const titleId = useId()
  const Heading = `h${headingLevel}` as const

  return (
    <article
      {...rest}
      className={['ui-card', className].filter(Boolean).join(' ')}
      aria-labelledby={title ? titleId : undefined}
    >
      {(title || description) && (
        <header className="ui-card-header">
          {title && <Heading id={titleId} className="ui-card-title">{title}</Heading>}
          {description && <p className="ui-card-description">{description}</p>}
        </header>
      )}
      {children && <div className="ui-card-body">{children}</div>}
      {actions && <footer className="ui-card-actions">{actions}</footer>}
    </article>
  )
}
