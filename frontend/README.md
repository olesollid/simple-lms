# Canvas frontend

React, TypeScript, and Vite frontend for the Spring Boot LMS.

## Requirements

Use Node.js 22.22.2 and its bundled npm. The version is recorded in `.nvmrc`
and used by the GitLab frontend job. Run frontend commands from this directory.
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
The home page now lists courses through the feature service. Enable mock mode
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

Run lint, tests and build before opening a merge request. TypeScript strict checking is
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
  App.tsx        Application entry component; routing can be added later
  main.tsx       Mounts React into the HTML document
```

See the services README for request examples and error handling. Keep
component-specific types with their component.

## Running alongside Spring Boot

Start the backend in another terminal from the repository root:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

On Linux/macOS use `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`.
If the wrapper is unavailable, use an installed Maven with the same arguments.
The backend normally listens on http://localhost:8080/.
See the root README for JDK requirements and H2 console instructions.

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

## GitLab CI

The root `.gitlab-ci.yml` defines three frontend jobs for merge requests and the
default branch. They share a hidden `.frontend` template that uses Node.js
22.22.2 and installs with `npm ci`:

| Job | Stage | What it does |
| --- | --- | --- |
| `frontend-lint` | test | `npm run lint` |
| `frontend-test` | test | `npm test`; publishes a JUnit report so results show in the merge request |
| `frontend-build` | build | `npm run build`; retains `frontend/dist/` as an artifact for one week |

The npm download cache is keyed by the lockfile. Generated `node_modules/`,
`.npm/`, and `dist/` directories are ignored by Git.

The artifact is the frontend build output; deploying or packaging it with the
backend is a separate task.
