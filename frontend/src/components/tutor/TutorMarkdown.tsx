import Markdown from 'react-markdown'
import type { Components } from 'react-markdown'
import { Link } from 'react-router'

// Model output and course material are untrusted. react-markdown never renders raw HTML, and
// images are dropped entirely so a manipulated answer cannot load (or leak data to) a remote URL.
const components: Components = {
  // Headings inside a chat bubble must not disturb the page's heading outline.
  h1: ({ children }) => <p className="tutor-md-heading">{children}</p>,
  h2: ({ children }) => <p className="tutor-md-heading">{children}</p>,
  h3: ({ children }) => <p className="tutor-md-heading">{children}</p>,
  h4: ({ children }) => <p className="tutor-md-heading">{children}</p>,
  h5: ({ children }) => <p className="tutor-md-heading">{children}</p>,
  h6: ({ children }) => <p className="tutor-md-heading">{children}</p>,
  a: ({ href, children }) => {
    if (href && href.startsWith('/') && !href.startsWith('//')) {
      return <Link to={href}>{children}</Link>
    }
    if (href && /^https?:\/\//i.test(href)) {
      return <a href={href} target="_blank" rel="noopener noreferrer">{children}</a>
    }
    return <span>{children}</span>
  },
}

export default function TutorMarkdown({ children }: { children: string }) {
  return (
    <div className="tutor-md">
      <Markdown components={components} disallowedElements={['img']} unwrapDisallowed>
        {children}
      </Markdown>
    </div>
  )
}
