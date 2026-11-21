export interface ModuleRequest {
  title: string
}

export interface ModuleResponse {
  id: number
  courseId: number
  title: string
  /** Teacher-chosen order; prerequisites are still listed first. */
  position?: number
  dependencyIds: number[]
  locked: boolean
}

export interface ModuleDeleteResponse {
  deletedModuleId: number
  affectedModuleIds: number[]
}
