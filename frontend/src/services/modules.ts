import type { ModuleDeleteResponse, ModuleRequest, ModuleResponse } from '../types/module.ts'
import type { ApiCallOptions, ApiRequest } from './apiClient.ts'

export function createModuleService(request: ApiRequest) {
  return {
    getModules: (courseId: number, options?: ApiCallOptions) =>
      request<ModuleResponse[]>(`/courses/${courseId}/modules`, options),
    getModule: (id: number, options?: ApiCallOptions) =>
      request<ModuleResponse>(`/modules/${id}`, options),
    createModule: (courseId: number, body: ModuleRequest, options?: ApiCallOptions) =>
      request<ModuleResponse>(`/courses/${courseId}/modules`, { ...options, method: 'POST', body }),
    updateModule: (id: number, body: ModuleRequest, options?: ApiCallOptions) =>
      request<ModuleResponse>(`/modules/${id}`, { ...options, method: 'PUT', body }),
    deleteModule: (id: number, options?: ApiCallOptions) =>
      request<ModuleDeleteResponse>(`/modules/${id}`, { ...options, method: 'DELETE' }),
    addDependency: (id: number, dependencyId: number, options?: ApiCallOptions) =>
      request<ModuleResponse>(`/modules/${id}/dependencies/${dependencyId}`, {
        ...options, method: 'POST',
      }),
    removeDependency: (id: number, dependencyId: number, options?: ApiCallOptions) =>
      request<void>(`/modules/${id}/dependencies/${dependencyId}`, {
        ...options, method: 'DELETE',
      }),
  }
}
