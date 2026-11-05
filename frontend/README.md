# Canvas frontend

React, TypeScript, and Vite frontend for the Spring Boot LMS.

## Requirements

Use Node.js 22.19.0 and its bundled npm. The version is recorded in `.nvmrc`
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
The starter page and counter run without the backend.

`npm ci` installs the versions in `package-lock.json`. Keep this file in version
control. Use `npm install <package>` when intentionally adding dependencies,
and include both package files in the change.

## Commands

Use `npm.cmd` instead of `npm` in PowerShell if needed.

| Command | Purpose |
| --- | --- |
| `npm run dev` | Start Vite with live updates |
| `npm run lint` | Run ESLint, including React Hooks rules |
| `npm run build` | Check TypeScript and build static assets into `dist/` |
| `npm run preview` | Serve the latest build locally, normally on port 4173 |

Run lint and build before opening a merge request. TypeScript strict checking is
active for both application code and Vite configuration. Preview requires a
successful build and is for local verification, not production hosting.

## Source layout

```text
src/
  components/    Reusable UI (currently the starter Counter)
  pages/         Complete screens (currently the starter HomePage)
  services/      Future API communication, grouped by feature
  types/         Future shared TypeScript types, including API DTOs
  assets/        Images and other imported assets
  App.tsx        Application entry component; routing can be added later
  main.tsx       Mounts React into the HTML document
```

The services and types directories contain guidance until the first API feature
needs them. Keep component-specific types with their component.

## Running alongside Spring Boot

Start the backend in another terminal from the repository root:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

On Linux/macOS use `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`.
If the wrapper is unavailable, use an installed Maven with the same arguments.
The backend normally listens on http://localhost:8080/.
See the root README for JDK requirements and H2 console instructions.

The starter does not call the API yet. Configure a Vite `/api` proxy and the
Spring Security login/CSRF flow when implementing the first API-connected page.
Frontend builds do not require a running backend or H2 database.

## GitLab CI

The root `.gitlab-ci.yml` defines `frontend-build` for merge requests and the
default branch. It uses Node.js 22.19.0, installs with `npm ci`, runs lint and
build, then retains `frontend/dist/` as a downloadable artifact for one week.
The npm download cache is keyed by the lockfile. Generated `node_modules/`,
`.npm/`, and `dist/` directories are ignored by Git.

The artifact is the frontend build output; deploying or packaging it with the
backend is a separate task.
