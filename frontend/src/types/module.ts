export interface ModuleRequest {
  title: string
}

export interface ModuleResponse {
  id: number
  courseId: number
  title: string
  dependencyIds: number[]
  locked: boolean
}

export interface ModuleDeleteResponse {
  deletedModuleId: number
  affectedModuleIds: number[]
}
