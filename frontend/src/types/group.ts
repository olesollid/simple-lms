export interface GroupResponse {
  id: number
  courseId: number
  name: string
}

export interface GroupMembershipResponse {
  id: number
  groupId: number
  email: string
  joinedAt: string
}
