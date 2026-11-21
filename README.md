### Frontend development

The React + TypeScript application lives in [frontend](frontend/README.md).
Use Node.js 22.22.2 (also used in GitLab CI), then run from `frontend/`:

```powershell
npm.cmd ci
npm.cmd run dev
```

On Linux/macOS use `npm` instead of `npm.cmd`. Run `npm run lint`, `npm test` and
`npm run build` before opening a merge request. See the frontend README for
testing, preview, folder structure, and backend startup instructions.
The `frontend-lint`, `frontend-test` and `frontend-build` CI jobs run on merge
requests and the default branch; the build job keeps `frontend/dist/` as an artifact.

### Local development with H2

Install JDK 25. No separate database server is required: Spring Boot starts H2
inside the application. Spring Data JPA and H2 dependencies are already in pom.xml;
Hibernate is supplied by the JPA starter.

Start from the directory containing pom.xml:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

On Linux/macOS use `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`.
If the wrapper is unavailable, use an installed Maven with the same arguments.
The dev profile disables automatic Docker Compose startup. Redis and RabbitMQ
are not required for course/module/content operations, but **are** required
to run the app at all once quiz grading is involved -- see
[Quiz submission grading](#quiz-submission-grading) below.

Open http://localhost:8080/h2-console/ and connect with:

| Setting | Value |
| --- | --- |
| Driver class | `org.h2.Driver` |
| JDBC URL | `jdbc:h2:mem:canvas2;DB_CLOSE_ON_EXIT=FALSE` |
| User | `sa` |
| Password | Leave empty |

The console is enabled only in the dev profile and rejects remote access.
Its security exception applies only to console requests; application endpoints
still require authentication. Spring Boot prints the generated development login
password at startup (username `user`).

For the default and dev profiles, `application.properties` defines the in-memory datasource. Hibernate's
`ddl-auto=create-drop` creates tables at startup and drops them at shutdown;
schema errors fail startup. All data is disposable and lost on restart.
`open-in-view=false` keeps database work out of web response rendering.

### Deployment (Docker)

| Setting | Purpose | Default |
| --- | --- | --- |
| `FRONTEND_URL` | Public URL of the app, used for the post-login redirect and CORS | `http://localhost:8080` |
| `GITHUB_CLIENT_ID` / `GITHUB_CLIENT_SECRET` | GitHub OAuth credentials | `dummy` in compose |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | Google OAuth credentials | `dummy` in compose |
| `RABBITMQ_HOST`, `REDIS_HOST` | Broker and cache hosts | set to the compose service names |

For a real deployment, set `FRONTEND_URL` to the public address and register
`<that address>/login/oauth2/code/github` and `.../google` as redirect URIs with each
provider. Change the default RabbitMQ credentials in `compose.yaml`.

Non-API paths (such as `/courses/1`) return the frontend so refreshes and deep links
work; `/api/**` and `/actuator/**` still require login. On GitLab, the `docker-build`
job builds this image and pushes it to the registry on the default branch.

Local debugging is unchanged: `docker compose up rabbitmq redis`, `npm run dev` in
`frontend/`, and the backend with the `dev` profile. Stop the prod `app` container
first, since it also uses port 8080.

### Persistent H2 (prod profile)

`application-prod.properties` uses `jdbc:h2:file:/data/canvas2;DB_CLOSE_ON_EXIT=FALSE`
and `ddl-auto=update`. Docker Compose mounts the named `canvas2-data` volume at
`/data`, so the database survives application/container restarts and image rebuilds.
The H2 console is disabled. Dev and tests continue to use disposable in-memory H2.

The Dockerfile is a self-contained multi-stage build: it builds the frontend,
bundles it into the Spring Boot jar, and produces one runtime image. No local Maven
or Node build is needed first:

```powershell
docker compose --profile prod up -d --build
```

Open http://localhost:8080. The app serves both the UI and the API from the same
origin, so no separate Vite server is needed in prod. Compose activates the Spring
`prod` profile (the image also defaults to it) and connects the app to RabbitMQ
and Redis by their service names. It forwards OAuth credentials from the host
environment or root `.env`; provide real credentials to sign in. `D250_REG`
optionally sets the image namespace; without it the local image is
`canvas2/app:latest`.

To verify a container restart, create a course, module, content, and submission
through the app, run `docker compose restart app`, sign in again if necessary,
and confirm those records and grades remain. Run
`docker compose --profile prod up -d --build --force-recreate app` to check
container replacement while keeping the same volume.

To try persistence directly on Windows without a container for the backend:

```powershell
$env:DATABASE_URL="jdbc:h2:file:./data/canvas2;DB_CLOSE_ON_EXIT=FALSE"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=prod"
```

Start RabbitMQ and Redis first as described below. Local database files are
under the ignored `data/` directory. Remove the `DATABASE_URL` environment
override before returning to the usual dev setup. `DATABASE_USERNAME`,
`DATABASE_PASSWORD`, and `FRONTEND_URL` can also override prod defaults.

Normal prod restarts retain quiz IDs, preventing a new quiz from reusing an old
quiz's Redis grade key. Deliberately deleting/restoring an older database, or
using disposable dev H2 with an existing Redis instance, can still leave stale
grades: reset the associated Redis quiz grades and pending RabbitMQ grading
events when resetting the database. Keep separate infrastructure for dev and prod.

To deliberately erase the Compose database, stop the stack with
`docker compose --profile prod down`, identify the project's volume using
`docker volume ls` (normally `<project>_canvas2-data`), then remove that specific
volume with `docker volume rm <volume-name>`. This deletes all relational data.
Ordinary `down` preserves the volume; do not use `down -v` unless a data reset
is intended. Clear the associated grading data/events as described above.

`ddl-auto=update` handles basic development schema evolution, but not every
change (such as renamed columns). A real deployment should use reviewed Flyway
migrations and backups. The restart regression test uses a temporary file
database across two application contexts; it does not require Docker.

Content uses JOINED inheritance: shared fields live in `contents`, while
subtype fields live in separate tables. This avoids conflicting subtype
constraints on the shared columns in the previous SINGLE_TABLE mapping.
This changes the schema layout; an existing persistent database would need a migration.

Run automated checks:

```powershell
.\mvnw.cmd test
```

Repository tests cover users, courses, enrollments, modules, groups and memberships.
H2DatabaseTests starts the actual web application using the `h2console` profile
(not `dev` -- see below), saves and reloads all four content types and student
interactions, and checks console access and API authentication.

`QuizGradingPipelineIntegrationTests` is the one exception: it needs a local Docker
daemon, since it uses Testcontainers to start disposable RabbitMQ and Redis containers
and prove the submission-grading pipeline against real infrastructure. GitLab CI's
runner doesn't expose a usable Docker daemon, so this one test is excluded there via
`-Dsurefire.excludesFile=ci-excluded-tests.txt`; run it yourself locally with
`./mvnw test -Dtest=QuizGradingPipelineIntegrationTests` whenever you touch the
grading pipeline.

Use the prod profile described above to retain data across restarts. Changing
the datasource URL alone in dev does not prevent create-drop from deleting data.
For deployment, leave the dev profile off.

### Quiz submission grading

Quiz submissions are graded asynchronously: a student's answers are published
as an event to RabbitMQ, a listener consumes it, grades it, and stores the
result in Redis. This runs under the `dev` and `prod` profiles -- `SubmissionGradingListener`
is profile-gated so the rest of the test suite, CI, and
a no-profile run never need a reachable broker.

To run the app with quiz grading working, start RabbitMQ and Redis first:

```powershell
docker compose up rabbitmq redis -d
```

Then start the app with the `dev` profile as above. Connection details
(host, port, credentials) are in `application-dev.properties`, matching
`compose.yaml`. Without these containers running, the app still starts, but
`POST /api/quizzes/{quizId}/submissions` will fail since the RabbitMQ listener
can't connect.

The profiles serve different purposes: `dev` enables the H2 console and quiz
grading with disposable data; `prod` enables quiz grading with persistent data
and no H2 console. `h2console` is console-only, used by `H2DatabaseTests` so that
test doesn't need RabbitMQ or Redis at all.

Quiz-related endpoints (all under `/api`, teacher-only writes unless noted):

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/modules/{moduleId}/quizzes` | Create a quiz (teacher) |
| `POST` | `/quizzes/{quizId}/questions` | Add a question with answer options, supports multi-select (teacher) |
| `GET` | `/quizzes/{quizId}/questions` | List questions without correct-answer flags (any enrolled member) |
| `GET` | `/quizzes/{quizId}/questions/with-answers` | List questions including correct-answer flags (teacher) |
| `POST` | `/quizzes/{quizId}/submissions` | Submit answers for grading; returns 202, grading happens async (any enrolled member) |
| `GET` | `/quizzes/{quizId}/grade` | Read the caller's own grade; 404 until grading completes (any enrolled member) |

### Assignment and coding-exercise deadlines

Teachers can set an optional deadline and enable late hand-ins when creating an
assignment or coding exercise. On its detail page, use **Edit deadline and late
policy** to change or remove the deadline later. This feature does not change
quiz submission rules.

Deadlines are ISO local date-times interpreted in **Europe/Oslo**, including
inside Docker; the browser sends the entered wall-clock value without converting
it to the browser's time zone. The backend rejects nonexistent or ambiguous
times during the daylight-saving clock change. A hand-in exactly at the deadline
is on time. Later text/file hand-ins and resubmissions are rejected with HTTP 403
unless late submissions are accepted. No deadline means submissions stay open.
The server checks the time after upload receipt, so uploads must finish before
the deadline if late submissions are disabled. These rules also apply to teacher
hand-ins; teachers can reopen a deadline through the policy editor.

Each accepted hand-in records its submission time in Europe/Oslo and its
`lateSubmission` status. The student and teacher views show that status. Changing
a deadline does not reclassify previous work; resubmitting updates the timestamp
and late status for the replacement. Rejected hand-ins preserve existing work.
Groups share the same status. Graded hand-ins remain locked regardless of policy.
Existing submissions from before this feature have unknown (`null`) late status
and are not retroactively labelled on time or late.

API: teacher-only `PUT /api/contents/{id}/submission-policy` accepts
`{"deadline":"2026-11-30T12:00:00","lateSubmissionsAccepted":true}`; use a null
deadline to remove the limit. The response is the updated content. Existing JSON
and multipart submission endpoints both enforce the policy and include
`lateSubmission` in their response metadata.

### File hand-ins

Assignment and coding-exercise submissions accept one PDF, DOC or DOCX attachment,
up to 10 MiB (10,485,760 bytes), with optional text. Text-only hand-ins still work.
Students choose a document in the submission form; teachers can download it from
the submissions review. An ungraded resubmission replaces the previous attachment;
resubmitting text only removes it. Graded submissions cannot be changed. Group
members share one attachment and submission, and course teachers can review it.

File contents are stored as a JPA `@Lob` byte array (H2 BLOB) on the submission,
alongside filename and content type. This keeps file replacement/deletion in the
same relational transaction and avoids another storage service. The tradeoff is
larger databases/backups and loading bounded file bytes when fetching submissions;
object storage would be preferable for larger-scale use. JSON responses contain
only file metadata, not bytes. The server enforces size, extension and basic file
signature checks; these checks are not full document parsing or malware scanning.

API: JSON `POST /api/contents/{id}/submissions` remains supported. File hand-ins
use multipart POST to the same URL with fields `file` and optional `text`.
`GET /api/submissions/{id}/file` returns an attachment download after checking
course enrollment and submission ownership/group membership or teacher role.
The multipart request limit is 11 MiB to allow envelope overhead; the file itself
is limited to 10 MiB. Existing session/CSRF protection applies to uploads.

BLOBs have the same lifetime as the relational database. In-memory H2 with
`create-drop` loses attachments on restart. Merge the persistent-database work
and use its file-based configuration/volume to retain them. The file persistence
test explicitly opens the same temporary file database across two app starts.
Mock mode remains read-only; test uploads against the real backend.

### Java Spring template project

This project is based on a GitLab [Project Template](https://docs.gitlab.com/ee/gitlab-basics/create-project.html).

Improvements can be proposed in the [original project](https://gitlab.com/gitlab-org/project-templates/spring).

### CI/CD with Auto DevOps

This template is compatible with [Auto DevOps](https://docs.gitlab.com/ee/topics/autodevops/).

If Auto DevOps is not already enabled for this project, you can [turn it on](https://docs.gitlab.com/ee/topics/autodevops/#enabling-auto-devops) in the project settings.
