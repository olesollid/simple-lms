import ModuleContents from './ModuleContents'
import type { ModuleResponse } from '../types/module'

export default function ModuleList({ modules }: { modules: ModuleResponse[] }) {
  if (modules.length === 0) {
    return (
      <div className="course-state" role="status">
        <h2>No modules yet</h2>
        <p>Modules will appear here once they are added to this course.</p>
      </div>
    )
  }

  return (
    <ul className="module-list" aria-label="Course modules">
      {modules.map(module => (
        <li key={module.id} className="module-card">
          <h3>{module.title}</h3>
          <ModuleContents moduleId={module.id} />
        </li>
      ))}
    </ul>
  )
}
