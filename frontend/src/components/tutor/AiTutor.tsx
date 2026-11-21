import { Component, lazy, Suspense } from 'react'
import type { ReactNode } from 'react'

// Loaded on demand so the tutor never delays the first render of a page.
const TutorWidget = lazy(() => import('./TutorWidget'))

// If the widget crashes or its chunk fails to load, the rest of the page must keep working.
class TutorErrorBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false }

  static getDerivedStateFromError() {
    return { failed: true }
  }

  render() {
    return this.state.failed ? null : this.props.children
  }
}

export default function AiTutor() {
  return (
    <TutorErrorBoundary>
      <Suspense fallback={null}>
        <TutorWidget />
      </Suspense>
    </TutorErrorBoundary>
  )
}
