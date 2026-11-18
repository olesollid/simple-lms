import type { ApiRequest } from './apiClient.ts'
import { createCourseService } from './courses.ts'
import { createUserService } from './users.ts'
import { createEnrollmentService } from './enrollments.ts'
import { createModuleService } from './modules.ts'
import { createContentService } from './contents.ts'
import { createSubmissionService } from './submissions.ts'
import { createGroupService } from './groups.ts'

export function createServices(request: ApiRequest) {
  return {
    courses: createCourseService(request),
    users: createUserService(request),
    enrollments: createEnrollmentService(request),
    modules: createModuleService(request),
    contents: createContentService(request),
    submissions: createSubmissionService(request),
    groups: createGroupService(request),
  }
}
