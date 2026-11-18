import { useState } from 'react'
import ModuleContents from './ModuleContents'
import CreateContentModal from './CreateContentModal'
import Button from './ui/Button'
import ErrorMessage from './ui/ErrorMessage'
import { modules } from '../services'
import { ApiError, ApiResponseError } from '../services/apiClient'
import type { ModuleResponse } from '../types/module'

function deleteError(error: unknown): string {
  if (error instanceof ApiError) return error.message
  if (error instanceof ApiResponseError) return 'The server returned an unexpected response.'
  return 'Check your connection and try again.'
}

export default function ModuleList({ modules: moduleList, isTeacher, onModuleDeleted }: {
  modules: ModuleResponse[]
  isTeacher: boolean
  onModuleDeleted: () => void
}) {
  if (moduleList.length === 0) {
    return (
      <div className="course-state" role="status">
        <h2>No modules yet</h2>
        <p>Modules will appear here once they are added to this course.</p>
      </div>
    )
  }

  return (
    <ul className="module-list" aria-label="Course modules">
      {moduleList.map(module => (
        <li key={module.id} className="module-card">
          <ModuleCard module={module} isTeacher={isTeacher} onModuleDeleted={onModuleDeleted} />
        </li>
      ))}
    </ul>
  )
}

function ModuleCard({ module, isTeacher, onModuleDeleted }: {
  module: ModuleResponse
  isTeacher: boolean
  onModuleDeleted: () => void
}) {
  const [addContentOpen, setAddContentOpen] = useState(false)
  const [refreshKey, setRefreshKey] = useState(0)
  const [deleting, setDeleting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleDeleteModule() {
    if (!window.confirm(`Delete module "${module.title}"? This cannot be undone.`)) return
    setDeleting(true)
    setError(null)
    try {
      await modules.deleteModule(module.id, {})
      onModuleDeleted()
    } catch (deleteAttemptError) {
      setError(deleteError(deleteAttemptError))
      setDeleting(false)
    }
  }

  return (
    <>
      <div className="module-card-header">
        <h3>{module.title}</h3>
        {isTeacher && (
          <div className="page-heading-actions">
            <Button variant="secondary" onClick={() => setAddContentOpen(true)}>Add content</Button>
            <Button variant="danger" onClick={handleDeleteModule} loading={deleting}>
              Delete module
            </Button>
          </div>
        )}
      </div>
      {error && <ErrorMessage message={error} />}
      <ModuleContents
        courseId={module.courseId}
        moduleId={module.id}
        isTeacher={isTeacher}
        key={refreshKey}
      />
      {isTeacher && (
        <CreateContentModal
          moduleId={module.id}
          open={addContentOpen}
          onClose={() => setAddContentOpen(false)}
          onCreated={() => {
            setAddContentOpen(false)
            setRefreshKey(previous => previous + 1)
          }}
        />
      )}
    </>
  )
}
