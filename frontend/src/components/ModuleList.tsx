import { useState } from 'react'
import ModuleContents from './ModuleContents'
import CreateContentModal from './CreateContentModal'
import Button from './ui/Button'
import ErrorMessage from './ui/ErrorMessage'
import { modules } from '../services'
import { ApiError, ApiResponseError } from '../services/apiClient'
import type { ModuleResponse } from '../types/module'

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message
  if (error instanceof ApiResponseError) return 'The server returned an unexpected response.'
  return 'Check your connection and try again.'
}

function titlesOf(ids: number[], allModules: ModuleResponse[]): string {
  const titles = ids
    .map(id => allModules.find(module => module.id === id)?.title)
    .filter((title): title is string => title != null)
  if (titles.length <= 1) return titles[0] ?? 'the prerequisite modules'
  return `${titles.slice(0, -1).join(', ')} and ${titles[titles.length - 1]}`
}

export default function ModuleList({
  modules: moduleList, isTeacher, completedContentIds = [], onModuleDeleted, onModulesChanged,
}: {
  modules: ModuleResponse[]
  isTeacher: boolean
  /** Content the student has completed; empty for teachers. */
  completedContentIds?: number[]
  onModuleDeleted: () => void
  /** Called after prerequisites change, so lock states can be reloaded. */
  onModulesChanged?: () => void
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
      {moduleList.map((module, index) => (
        <li key={module.id} className="module-card">
          <ModuleCard
            module={module}
            index={index}
            allModules={moduleList}
            isTeacher={isTeacher}
            completedContentIds={completedContentIds}
            onModuleDeleted={onModuleDeleted}
            onModulesChanged={onModulesChanged ?? onModuleDeleted}
          />
        </li>
      ))}
    </ul>
  )
}

function ModuleCard({ module, index, allModules, isTeacher, completedContentIds, onModuleDeleted, onModulesChanged }: {
  module: ModuleResponse
  index: number
  allModules: ModuleResponse[]
  isTeacher: boolean
  completedContentIds: number[]
  onModuleDeleted: () => void
  onModulesChanged: () => void
}) {
  const [addContentOpen, setAddContentOpen] = useState(false)
  const [refreshKey, setRefreshKey] = useState(0)
  const [deleting, setDeleting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function move(offset: -1 | 1) {
    const ids = allModules.map(other => other.id)
    const target = index + offset
    ;[ids[index], ids[target]] = [ids[target], ids[index]]
    setError(null)
    try {
      await modules.reorder(module.courseId, ids, {})
      onModulesChanged()
    } catch (moveError) {
      setError(errorMessage(moveError))
    }
  }

  async function handleDeleteModule() {
    if (!window.confirm(`Delete module "${module.title}"? This cannot be undone.`)) return
    setDeleting(true)
    setError(null)
    try {
      await modules.deleteModule(module.id, {})
      onModuleDeleted()
    } catch (deleteAttemptError) {
      setError(errorMessage(deleteAttemptError))
      setDeleting(false)
    }
  }

  return (
    <>
      <div className="module-card-header">
        <div className="module-card-title">
          <h3>{module.title}</h3>
          {module.locked && <span className="lock-badge">Locked</span>}
        </div>
        {isTeacher && (
          <div className="page-heading-actions">
            {allModules.length > 1 && (
              <span className="reorder-buttons">
                <button type="button" className="reorder-button" aria-label={`Move ${module.title} up`}
                  disabled={index === 0} onClick={() => move(-1)}>↑</button>
                <button type="button" className="reorder-button" aria-label={`Move ${module.title} down`}
                  disabled={index === allModules.length - 1} onClick={() => move(1)}>↓</button>
              </span>
            )}
            <Button variant="secondary" onClick={() => setAddContentOpen(true)}>Add content</Button>
            <Button variant="danger" onClick={handleDeleteModule} loading={deleting}>
              Delete module
            </Button>
          </div>
        )}
      </div>
      {error && <ErrorMessage message={error} />}
      {isTeacher ? (
        <ModulePrerequisites module={module} allModules={allModules} onChanged={onModulesChanged} />
      ) : module.dependencyIds.length > 0 && !module.locked && (
        <p className="module-prerequisites-note">Unlocked by completing {titlesOf(module.dependencyIds, allModules)}.</p>
      )}
      {module.locked ? (
        <p className="module-placeholder">
          Complete every item in {titlesOf(module.dependencyIds, allModules)} to unlock this module.
        </p>
      ) : (
        <ModuleContents
          courseId={module.courseId}
          moduleId={module.id}
          isTeacher={isTeacher}
          completedContentIds={isTeacher ? undefined : completedContentIds}
          showProgress={allModules.some(other => other.dependencyIds.includes(module.id))}
          key={refreshKey}
        />
      )}
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

/**
 * Teacher control for "this module requires …": students see a module only once every item in
 * each prerequisite module is completed. The server rejects cycles (A needs B needs A).
 */
function ModulePrerequisites({ module, allModules, onChanged }: {
  module: ModuleResponse
  allModules: ModuleResponse[]
  onChanged: () => void
}) {
  const [selected, setSelected] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const selectId = `prerequisite-${module.id}`
  const candidates = allModules.filter(other =>
    other.id !== module.id && !module.dependencyIds.includes(other.id))

  async function run(action: () => Promise<unknown>) {
    setSaving(true)
    setError(null)
    try {
      await action()
      setSelected('')
      onChanged()
    } catch (actionError) {
      setError(errorMessage(actionError))
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="module-prerequisites">
      <span className="module-prerequisites-label">Prerequisites:</span>
      {module.dependencyIds.length === 0 && <span className="module-prerequisites-none">None, always open</span>}
      {module.dependencyIds.map(id => {
        const title = allModules.find(other => other.id === id)?.title ?? `Module ${id}`
        return (
          <span key={id} className="prerequisite-chip">
            {title}
            <button
              type="button"
              className="prerequisite-remove"
              aria-label={`Remove prerequisite ${title}`}
              disabled={saving}
              onClick={() => run(() => modules.removeDependency(module.id, id, {}))}
            >
              ✕
            </button>
          </span>
        )
      })}
      {candidates.length > 0 && (
        <span className="prerequisite-add">
          <label className="visually-hidden" htmlFor={selectId}>Add prerequisite to {module.title}</label>
          <select
            id={selectId}
            className="ui-input"
            value={selected}
            disabled={saving}
            onChange={event => setSelected(event.target.value)}
          >
            <option value="">Add prerequisite…</option>
            {candidates.map(other => <option key={other.id} value={other.id}>{other.title}</option>)}
          </select>
          <Button
            variant="secondary"
            disabled={!selected}
            loading={saving}
            onClick={() => run(() => modules.addDependency(module.id, Number(selected), {}))}
          >
            Add
          </Button>
        </span>
      )}
      {error && <ErrorMessage message={error} />}
    </div>
  )
}
