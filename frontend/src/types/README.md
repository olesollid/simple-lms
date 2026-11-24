# Types

These interfaces match the backend's request and response DTOs in
`src/main/java/com/_gods/simplelms/web/dto`, not its JPA entities:

- `user.ts`: UserRequest and UserResponse.
- `course.ts`: CourseRequest, CourseResponse, and Term.
- `enrollment.ts`: EnrollmentRequest, EnrollmentResponse, and Role.
- `module.ts`: ModuleRequest, ModuleResponse, and ModuleDeleteResponse.

Import them with `import type`, for example:

```typescript
import type { CourseResponse } from '../types/course'
```

JSON numbers map to `number`, enum names to string unions, lists to arrays,
and LocalDateTime to a date-time string without a timezone offset.
Keep these declarations in sync when backend DTOs change. They provide compile-time
checking; they do not validate JSON at runtime or perform API requests.

Keep types used by only one component alongside that component.
