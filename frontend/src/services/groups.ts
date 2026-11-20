import type { GroupMembershipResponse, GroupResponse } from '../types/group.ts'
import type { ApiCallOptions, ApiRequest } from './apiClient.ts'

export function createGroupService(request: ApiRequest) {
  return {
    getGroupsByCourse: (courseId: number, options?: ApiCallOptions) =>
      request<GroupResponse[]>(`/courses/${courseId}/groups`, options),
    getOwnGroup: (courseId: number, options?: ApiCallOptions) =>
      request<GroupResponse>(`/courses/${courseId}/groups/mine`, options),
    createGroup: (courseId: number, name: string, options?: ApiCallOptions) =>
      request<GroupResponse>(`/courses/${courseId}/groups`, {
        ...options, method: 'POST', body: { name },
      }),
    getMembers: (groupId: number, options?: ApiCallOptions) =>
      request<GroupMembershipResponse[]>(`/groups/${groupId}/members`, options),
    addMember: (groupId: number, email: string, options?: ApiCallOptions) =>
      request<GroupMembershipResponse>(`/groups/${groupId}/members/${encodeURIComponent(email)}`, {
        ...options, method: 'POST',
      }),
    removeMember: (groupId: number, email: string, options?: ApiCallOptions) =>
      request<void>(`/groups/${groupId}/members/${encodeURIComponent(email)}`, {
        ...options, method: 'DELETE',
      }),
  }
}
