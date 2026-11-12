import type { CourseRequest, CourseResponse } from '../types/course.ts'
import type { ApiCallOptions, ApiRequest } from './apiClient.ts'

export function createCourseService(request: ApiRequest) {
  return {
    getCourses: (options?: ApiCallOptions) =>
      request<CourseResponse[]>('/courses', options),
    getCourse: (id: number, options?: ApiCallOptions) =>
      request<CourseResponse>(`/courses/${id}`, options),
    createCourse: (body: CourseRequest, options?: ApiCallOptions) =>
      request<CourseResponse>('/courses', { ...options, method: 'POST', body }),
    updateCourse: (id: number, body: CourseRequest, options?: ApiCallOptions) =>
      request<CourseResponse>(`/courses/${id}`, { ...options, method: 'PUT', body }),
    deleteCourse: (id: number, options?: ApiCallOptions) =>
      request<void>(`/courses/${id}`, { ...options, method: 'DELETE' }),
  }
}
