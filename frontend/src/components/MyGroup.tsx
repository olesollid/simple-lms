import { useEffect, useState } from 'react'
import { groups } from '../services'
import { ApiError } from '../services/apiClient'
import type { GroupMembershipResponse, GroupResponse } from '../types/group'

type MyGroupState =
  | { status: 'loading' }
  | { status: 'none' }
  | { status: 'error' }
  | { status: 'member'; group: GroupResponse; members: GroupMembershipResponse[] }

/** Shows the signed-in student's group in a course, or nothing if they are not in one. */
export default function MyGroup({ courseId }: { courseId: number }) {
  const [state, setState] = useState<MyGroupState>({ status: 'loading' })

  useEffect(() => {
    const controller = new AbortController()
    const options = { signal: controller.signal }

    async function load(): Promise<MyGroupState> {
      try {
        const group = await groups.getOwnGroup(courseId, options)
        const members = await groups.getMembers(group.id, options)
        return { status: 'member', group, members }
      } catch (error) {
        if (error instanceof ApiError && error.status === 404) return { status: 'none' }
        throw error
      }
    }

    load().then(
      result => {
        if (!controller.signal.aborted) setState(result)
      },
      () => {
        if (!controller.signal.aborted) setState({ status: 'error' })
      },
    )

    return () => controller.abort()
  }, [courseId])

  if (state.status === 'loading' || state.status === 'none') return null
  if (state.status === 'error') {
    return <p className="module-placeholder">Your group could not be loaded.</p>
  }

  return (
    <section className="group-card" aria-label="Your group">
      <p className="eyebrow">Your group</p>
      <h2>{state.group.name}</h2>
      <ul className="group-members">
        {state.members.map(member => <li key={member.email}>{member.email}</li>)}
      </ul>
    </section>
  )
}
