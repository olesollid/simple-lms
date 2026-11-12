import type { ApiRequest } from './apiClient.ts'
import { createCourseService } from './courses.ts'
import { createUserService } from './users.ts'
import { createEnrollmentService } from './enrollments.ts'
import { createModuleService } from './modules.ts'

export function createServices(request: ApiRequest) {
  return {
    courses: createCourseService(request),
    users: createUserService(request),
    enrollments: createEnrollmentService(request),
    modules: createModuleService(request),
  }
}
