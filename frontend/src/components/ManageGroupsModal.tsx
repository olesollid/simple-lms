import { useEffect, useState } from 'react'
import { enrollments, groups } from '../services'
import { ApiError, ApiResponseError } from '../services/apiClient'
import Modal from './ui/Modal'
import Input from './ui/Input'
import Button from './ui/Button'
import ErrorMessage from './ui/ErrorMessage'
import LoadingIndicator from './ui/LoadingIndicator'
import type { EnrollmentResponse } from '../types/enrollment'
import type { GroupMembershipResponse, GroupResponse } from '../types/group'

function requestError(error: unknown): string {
  if (error instanceof ApiError) return error.message
  if (error instanceof ApiResponseError) return 'The server returned an unexpected response.'
  return 'Check your connection and try again.'
}

interface GroupWithMembers {
  group: GroupResponse
  members: GroupMembershipResponse[]
}

type GroupsState =
  | { status: 'loading' }
  | { status: 'success'; students: EnrollmentResponse[]; groups: GroupWithMembers[] }
  | { status: 'error'; message: string }

export default function ManageGroupsModal({ courseId, open, onClose }: {
  courseId: number
  open: boolean
  onClose: () => void
}) {
  const [state, setState] = useState<GroupsState>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)
  const [wasOpen, setWasOpen] = useState(open)
  const [lastAttemptSeen, setLastAttemptSeen] = useState(attempt)
  const [name, setName] = useState('')
  const [creating, setCreating] = useState(false)
  const [selected, setSelected] = useState<Record<number, string>>({})
  const [busyKey, setBusyKey] = useState<string | null>(null)
  const [formError, setFormError] = useState<string | null>(null)

  if (open && (!wasOpen || lastAttemptSeen !== attempt)) {
    setWasOpen(true)
    setLastAttemptSeen(attempt)
    if (!wasOpen) setState({ status: 'loading' })
  } else if (!open && wasOpen) {
    setWasOpen(false)
  }

  useEffect(() => {
    if (!open) return

    const controller = new AbortController()
    const options = { signal: controller.signal }

    async function load() {
      const [students, groupList] = await Promise.all([
        enrollments.getEnrollments(courseId, 'STUDENT', options),
        groups.getGroupsByCourse(courseId, options),
      ])
      const memberLists = await Promise.all(
        groupList.map(group => groups.getMembers(group.id, options)),
      )
      return {
        students,
        groups: groupList.map((group, index) => ({ group, members: memberLists[index] })),
      }
    }

    load().then(
      result => {
        if (!controller.signal.aborted) setState({ status: 'success', ...result })
      },
      error => {
        if (!controller.signal.aborted) setState({ status: 'error', message: requestError(error) })
      },
    )

    return () => controller.abort()
  }, [open, courseId, attempt])

  function reload() {
    setAttempt(previous => previous + 1)
  }

  function handleClose() {
    setName('')
    setSelected({})
    setFormError(null)
    onClose()
  }

  async function handleCreate(event: React.FormEvent) {
    event.preventDefault()
    if (!name.trim()) {
      setFormError('Enter a group name.')
      return
    }
    setCreating(true)
    setFormError(null)
    try {
      await groups.createGroup(courseId, name.trim(), {})
      setName('')
      reload()
    } catch (error) {
      setFormError(requestError(error))
    } finally {
      setCreating(false)
    }
  }

  async function handleAdd(groupId: number) {
    const email = selected[groupId]
    if (!email) return
    setBusyKey(`add-${groupId}`)
    setFormError(null)
    try {
      await groups.addMember(groupId, email, {})
      setSelected(previous => ({ ...previous, [groupId]: '' }))
      reload()
    } catch (error) {
      setFormError(requestError(error))
    } finally {
      setBusyKey(null)
    }
  }

  async function handleRemove(groupId: number, email: string) {
    setBusyKey(`remove-${groupId}-${email}`)
    setFormError(null)
    try {
      await groups.removeMember(groupId, email, {})
      reload()
    } catch (error) {
      setFormError(requestError(error))
    } finally {
      setBusyKey(null)
    }
  }

  const nameByEmail = new Map<string, string>()
  if (state.status === 'success') {
    state.students.forEach(student => nameByEmail.set(student.email, student.name))
  }

  return (
    <Modal open={open} onClose={handleClose} title="Manage groups">
      <form onSubmit={handleCreate} className="create-course-form">
        <Input
          label="Group name"
          value={name}
          onChange={event => setName(event.target.value)}
          required
        />
        <Button type="submit" loading={creating}>Create group</Button>
      </form>
      {formError && <ErrorMessage message={formError} />}

      <div className="enrollment-roster">
        {state.status === 'loading' && <LoadingIndicator message="Loading groups…" />}
        {state.status === 'error' && (
          <ErrorMessage message={state.message}>
            <Button variant="secondary" onClick={reload}>Try again</Button>
          </ErrorMessage>
        )}
        {state.status === 'success' && state.groups.length === 0 && (
          <p className="module-placeholder">No groups yet. Create one above.</p>
        )}
        {state.status === 'success' && state.groups.map(({ group, members }) => {
          const assigned = new Set(
            state.groups.flatMap(entry => entry.members.map(member => member.email)),
          )
          const available = state.students.filter(student => !assigned.has(student.email))
          return (
            <section key={group.id} className="group-card" aria-label={`Group ${group.name}`}>
              <h3>{group.name}</h3>
              {members.length === 0 ? (
                <p className="module-placeholder">No members yet.</p>
              ) : (
                <ul className="roster-list">
                  {members.map(member => (
                    <li key={member.email} className="roster-item">
                      <span>{nameByEmail.get(member.email) ?? member.email}</span>
                      <span className="roster-email">{member.email}</span>
                      <Button
                        variant="secondary"
                        onClick={() => handleRemove(group.id, member.email)}
                        loading={busyKey === `remove-${group.id}-${member.email}`}
                      >
                        Remove
                      </Button>
                    </li>
                  ))}
                </ul>
              )}
              <div className="group-add">
                <label className="ui-label" htmlFor={`group-add-${group.id}`}>
                  Add student to {group.name}
                </label>
                <select
                  id={`group-add-${group.id}`}
                  className="ui-input"
                  value={selected[group.id] ?? ''}
                  onChange={event => setSelected(previous => ({
                    ...previous, [group.id]: event.target.value,
                  }))}
                  disabled={available.length === 0}
                >
                  <option value="">
                    {available.length === 0 ? 'No students without a group' : 'Select a student…'}
                  </option>
                  {available.map(student => (
                    <option key={student.email} value={student.email}>
                      {student.name} ({student.email})
                    </option>
                  ))}
                </select>
                <Button
                  variant="secondary"
                  onClick={() => handleAdd(group.id)}
                  loading={busyKey === `add-${group.id}`}
                  disabled={!selected[group.id]}
                >
                  Add
                </Button>
              </div>
            </section>
          )
        })}
      </div>
    </Modal>
  )
}
