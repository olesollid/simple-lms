# simple-lms frontend

React, TypeScript, and Vite frontend for the Spring Boot LMS.

## Requirements

Use Node.js 22.22.2 and its bundled npm. The version is recorded in `.nvmrc`
and used by the CI frontend job. Run frontend commands from this directory.
If your Node version manager supports `.nvmrc`, use it to select that version.

## Install and start

On Windows PowerShell:

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev
```

On Linux/macOS:

```sh
cd frontend
npm ci
npm run dev
```

`npm.cmd` avoids PowerShell execution-policy errors involving `npm.ps1`.
Open the URL printed by Vite, normally http://localhost:5173/.
The home page opens Courses, listing courses through the feature service. Enable mock mode
as described below to view sample courses without the backend.

`npm ci` installs the versions in `package-lock.json`. Keep this file in version
control. Use `npm install <package>` when intentionally adding dependencies,
and include both package files in the change.

## Commands

Use `npm.cmd` instead of `npm` in PowerShell if needed.

| Command | Purpose |
| --- | --- |
| `npm run dev` | Start Vite with live updates |
| `npm run lint` | Run ESLint, including React Hooks rules |
| `npm test` | Run API client, feature service, mock, and course rendering tests |
| `npm run build` | Check TypeScript and build static assets into `dist/` |
| `npm run preview` | Serve the latest build locally, normally on port 4173 |

Run lint, tests and build before opening a pull request. TypeScript strict checking is
active for both application code and Vite configuration. Preview requires a
successful build and is for local verification, not production hosting.

## Testing

Tests use [Vitest](https://vitest.dev/) with jsdom (a simulated browser) and
[React Testing Library](https://testing-library.com/docs/react-testing-library/intro/).
Tests include `src/**/*.test.ts`, `src/**/*.test.tsx`, and the migrated
`tests/**/*.test.mjs` API/service/rendering suites. All use Vitest and run through
the same `npm test` command locally and in CI. Shared setup is in
`src/test/setup.ts`, and the test options are in `vite.config.ts`.

```sh
npm test                                   # run everything once
npm run test:watch                         # watch mode while developing
npx vitest run src/components/CourseCard.test.tsx   # a single file
```

Query the page the way a user sees it (`getByRole`, `getByText`) and trigger
interactions with `userEvent`, rather than inspecting component internals.

## Source layout

```text
src/
  components/    Reusable UI, including CourseCard and CourseList
  pages/         CoursesPage and its loading/error state types
  services/      Shared API client, feature services, and mock transport
  types/         Shared TypeScript request/response DTO interfaces
  assets/        Images and other imported assets
  App.tsx        Routes within the shared application layout
  main.tsx       Mounts React with BrowserRouter
```

See the services README for request examples and error handling. Keep
component-specific types with their component.

## Navigation and layout

React Router provides `/courses` and `/profile`, along with course and content
detail routes. The root URL redirects to `/courses`, which serves as the home
page. Old `/dashboard` links also redirect to `/courses`.
Unknown URLs show a page-not-found view. The sidebar lists Courses and Profile,
and the header brand links back to Courses.

Profile loads the signed-in user's name and email automatically. Coursework is
grouped by current course, with published grades, submitted work awaiting grading
or publication, and work not submitted yet. Shared group hand-ins count as submitted.
Teacher courses link to administration instead of showing student coursework.
Only currently visible, unlocked activities are included; drafts and hidden/locked
content stay out of this view. Course summaries load independently and offer retry,
so a course/Redis failure does not hide personal information or other courses.

The self-only `GET /api/courses/{id}/my-deliveries` endpoint derives identity from
the session and checks enrollment. It withholds grades and feedback until release,
including quiz results. Refresh profile to retrieve newly published results. Mock
mode provides sample profile coursework; use the real backend to verify publication
and group permissions.

`AppLayout` shares a header, navigation sidebar, skip link, and main content
area across routes. Below 760px, the sidebar is controlled by the header's
menu button and closes after selecting a view. Active links identify the current
view. Route changes move keyboard focus to the main content.

Production hosting must serve `index.html` for frontend URLs such as `/profile`
so direct visits and refreshes work. Preserve backend `/api` routes when
configuring this fallback. Vite provides this fallback during development.

## Running alongside Spring Boot

Start the backend in another terminal from the repository root:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

On Linux/macOS use `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`.
If the wrapper is unavailable, use an installed Maven with the same arguments.
The backend normally listens on http://localhost:8080/.
See the root README for requirements and the full run instructions, and TECHNICAL.md for the H2 console.

The shared client defaults to `/api`; Vite proxies that path to port 8080.
To configure the base URL, copy `.env.example` to `.env.local` and edit
`VITE_API_BASE_URL`. Restart Vite after changing it. Vite substitutes these values
at build time, so changing production configuration requires rebuilding.
Values prefixed with VITE_ are public browser configuration, never secrets.

Set `VITE_USE_MOCK_API=true` in `.env.local` and restart Vite to browse read-only
sample data through the services without Spring Boot. It defaults to false.
Pages import services from `src/services/index.ts`; switching the environment
setting does not require page changes. Mock writes return an explicit 501 error.
See the services README for usage, fixtures, and authentication limitations.

CoursesPage calls `courses.getCourses()` when it mounts. It displays loading,
empty, success, and error states; Refresh courses/Try again starts a new request.
Requests are cancelled when the page unmounts. Course cards show title,
description, and semester; course-detail navigation is a separate feature.
An unauthenticated response shows a sign-in message. The login screen and
automatic navigation to it belong to the authentication work.
Frontend builds and automated tests do not require a running backend or H2.
Rendering tests cover the course list states; browser interaction testing is
not included in the current test setup.

## GitHub Actions

The `frontend` job in `.github/workflows/ci.yml` runs on pull requests and pushes to the default branch,
using Node.js 22.22.2 and `npm ci`. It runs, in order:

| Step | What it does |
| --- | --- |
| lint | `npm run lint` |
| test | `npm test`; uploads a JUnit report as a workflow artifact |
| build | `npm run build` |

The npm download cache is keyed by the lockfile (handled by `actions/setup-node`). Generated `node_modules/`,
`.npm/`, and `dist/` directories are ignored by Git.

The artifact is the frontend build output; deploying or packaging it with the
backend is a separate task.
