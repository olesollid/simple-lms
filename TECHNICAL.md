# Technical documentation

How the project is built and configured. To run it, see the [README](README.md).

Contents: [Stack](#stack) · [How it fits together](#how-it-fits-together) ·
[Configuration](#configuration) · [Persistence](#persistence) ·
[Quiz submission grading](#quiz-submission-grading) · [Quiz workflow](#quiz-workflow) ·
[Assignment and coding-exercise deadlines](#assignment-and-coding-exercise-deadlines) ·
[File hand-ins](#file-hand-ins) · [AI tutor](#ai-tutor) · [Deployment and CI](#deployment-and-ci) ·
[Testing](#testing)

## Stack

| Concern | Technology |
| --- | --- |
| Backend | Java 25, Spring Boot 4.1 (Spring MVC, Actuator) |
| Security | Spring Security with OAuth2 login (GitHub and Google), session cookie and CSRF protection |
| Persistence | Spring Data JPA with Hibernate, H2 (in memory for development, a file for production) |
| Messaging | RabbitMQ through Spring AMQP |
| Key-value store | Redis through Spring Data Redis |
| Frontend | React 19, TypeScript, React Router 7, Vite, `react-markdown` |
| AI | Google Gemini (Interactions REST API), called only from the backend |
| Build and deployment | Maven wrapper, npm, Docker multi-stage build, Docker Compose |
| CI | GitHub Actions |
| Tests | JUnit and Spring Boot test, Mockito, Testcontainers; Vitest and Testing Library; axe-core |

## How it fits together

- **One app in production.** The Spring Boot application serves the REST API (`/api/**`) and the built
  React app. Any other path returns the frontend, so refreshes and deep links such as `/courses/1`
  work. In development the frontend runs on Vite (port 5173) and proxies `/api`, `/oauth2`,
  `/login/oauth2` and `/logout` to the backend (port 8080).
- **Sign-in and roles.** Users sign in with GitHub or Google. Roles are per course: the same user can
  be a student in one course and a teacher in another. The API answers 401 when you are not signed in,
  and writes need the CSRF token that the frontend sends in `X-XSRF-TOKEN`.
- **Relational data** (users, courses, modules, content, submissions, groups and graded quiz
  attempts) lives in H2 through JPA.
- **Quiz grading is asynchronous.** A submission becomes a RabbitMQ event; a listener grades it,
  stores the attempt in the database and caches it in Redis. See
  [Quiz submission grading](#quiz-submission-grading).
- **The AI tutor** is a chat panel that streams answers from Gemini through the backend. See
  [AI tutor](#ai-tutor).

## Configuration

Spring profiles:

| Profile | Used for | Database | RabbitMQ and Redis |
| --- | --- | --- | --- |
| *(none)* | Tests and CI | In-memory H2 | Not needed |
| `dev` | Local development | In-memory H2, H2 console on | Needed for quiz grading |
| `prod` | Docker deployment | H2 file in a Docker volume | Needed |
| `h2console` | `H2DatabaseTests` only | In-memory H2 | Not needed |

Environment variables (set in `.env`; Docker Compose reads that file automatically):

| Variable | Purpose | Default |
| --- | --- | --- |
| `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET` | GitHub OAuth app | none in dev (required to start); `dummy` in Compose |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Google OAuth app | none in dev (required to start); `dummy` in Compose |
| `FRONTEND_URL` | Public URL of the frontend, used for the redirect after login and for CORS | `http://localhost:5173` (dev), `http://localhost:8080` (prod) |
| `AI_TUTOR_ENABLED` | Turns the AI tutor on for every course | `false` |
| `GEMINI_API_KEY` | Gemini API key; the tutor stays off without it | empty |
| `GEMINI_MODEL` | Gemini model used by the tutor | `gemini-3.5-flash-lite` |
| `AI_TUTOR_RATE_LIMIT`, `AI_TUTOR_RATE_WINDOW` | Chat requests allowed per user per window | `20` per `1m` |
| `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD` | RabbitMQ connection | `localhost`, `5672`, `myuser`, `secret`; Compose sets the host to `rabbitmq` |
| `REDIS_HOST`, `REDIS_PORT` | Redis connection | `localhost`, `6379`; Compose sets the host to `redis` |
| `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | Override the production database | H2 file `/data/simplelms` |
| `IMAGE_REGISTRY` | Registry path of the CI-built image; Compose runs `${IMAGE_REGISTRY}/app:latest` (see "Deploy to a host" in the README) | `simple-lms`, i.e. the locally built `simple-lms/app:latest` |

Both OAuth providers must allow the callback `http://localhost:8080/login/oauth2/code/github` and
`http://localhost:8080/login/oauth2/code/google`. This is the same in development, because the
backend builds the callback from its own address. For a real deployment, set `FRONTEND_URL` to the
public address and register `<that address>/login/oauth2/code/{github,google}` instead. Change the
default RabbitMQ credentials in `compose.yaml`.

## Persistence

**Development and tests** use an in-memory H2 database defined in `application.properties`.
Hibernate's `ddl-auto=create-drop` creates the tables at startup and drops them at shutdown, and schema
errors fail startup. All data is disposable and lost on restart. `open-in-view=false` keeps database
work out of web response rendering.

**H2 console (dev profile only).** Open http://localhost:8080/h2-console/ and connect with:

| Setting | Value |
| --- | --- |
| Driver class | `org.h2.Driver` |
| JDBC URL | `jdbc:h2:mem:simplelms;DB_CLOSE_ON_EXIT=FALSE` |
| User | `sa` |
| Password | Leave empty |

The console rejects remote access, and its security exception applies only to console requests;
application endpoints still require authentication.

**Production** (`application-prod.properties`) uses `jdbc:h2:file:/data/simplelms;DB_CLOSE_ON_EXIT=FALSE`
and `ddl-auto=update`. Docker Compose mounts the named `simplelms-data` volume at `/data`, so the database
survives application and container restarts and image rebuilds. Redis has its own volume
(`simplelms-redis`), but it only holds a cache that is rebuilt from the database, so losing it loses no
grades. The H2 console is disabled. Changing
the datasource URL alone in dev does not prevent `create-drop` from deleting data; use the `prod`
profile to keep data.

To try persistence without a container for the backend, start RabbitMQ and Redis, then run the backend
with the `prod` profile and a local file database, for example
`DATABASE_URL=jdbc:h2:file:./data/simplelms;DB_CLOSE_ON_EXIT=FALSE` (PowerShell: `$env:DATABASE_URL="..."`).
Local database files go in the ignored `data/` directory. `DATABASE_USERNAME`, `DATABASE_PASSWORD` and
`FRONTEND_URL` can also override prod defaults.

To verify a container restart, create a course, module, content and submission through the app, run
`docker compose restart app`, sign in again if necessary, and confirm the records and grades remain.
`docker compose --profile prod up -d --build --force-recreate app` checks container replacement while
keeping the same volume.

**Resetting data.** Quiz grades live in the database, so resetting the database resets them too.
Redis keys use a random key per quiz (`grade_key`) rather than the quiz id, so a new quiz never picks up
cached grades from an old one, even when the dev database starts empty while Redis keeps running. To
erase the Compose data on purpose, run `docker compose --profile prod down -v`, which removes both the
`simplelms-data` and `simplelms-redis` volumes. Ordinary `down` keeps them.

**Schema.** `ddl-auto=update` handles basic schema evolution, but not every change (such as renamed
columns); a real deployment should use reviewed Flyway migrations and backups. Content uses JOINED
inheritance: shared fields live in `contents`, while subtype fields live in separate tables. This avoids
conflicting subtype constraints on the shared columns in the previous SINGLE_TABLE mapping, and an
existing persistent database would need a migration for that change. The restart regression test uses a
temporary file database across two application contexts and does not require Docker.

## Quiz submission grading

Quiz submissions are graded asynchronously: a student's answers are published
as an event to RabbitMQ, a listener consumes it, grades it, and stores the
attempt in the database (`QuizAttempt`), which is the source of truth. Redis caches each
student's best and latest attempt and holds the atomic attempt counter; anything missing from Redis
is rebuilt from the database. This runs under the `dev` and `prod` profiles -- `SubmissionGradingListener`
is profile-gated so the rest of the test suite, CI, and
a no-profile run never need a reachable broker.

To run the app with quiz grading working, start RabbitMQ and Redis first (`docker compose up rabbitmq redis -d`),
then start the app with the `dev` profile, as described in the [README](README.md). Connection details
(host, port, credentials) are in `application-dev.properties`, matching
`compose.yaml`. Without these containers running, the app still starts, but
`POST /api/quizzes/{quizId}/submissions` will fail since the RabbitMQ listener
can't connect.

The profiles serve different purposes: `dev` enables the H2 console and quiz
grading with disposable data; `prod` enables quiz grading with persistent data
and no H2 console. `h2console` is console-only, used by `H2DatabaseTests` so that
test doesn't need RabbitMQ or Redis at all.

## Quiz workflow

A quiz moves through these stages, all controlled by the teacher:

1. **Draft.** Students cannot see it. The teacher adds, edits and deletes questions.
2. **Published.** Students can take it; the questions are frozen so every attempt is graded against
   the same answers. Attempts are graded automatically (see above).
3. **Results.** By default a student sees their score and hints as soon as an attempt is graded
   (`showResultsImmediately`, on by default). A teacher can turn that off per quiz and publish the
   grades later. Until they do, an attempt shows as pending while the grader is still working, and
   then as submitted, with no score and no hints. Publishing is one-way, needs the quiz to be
   published, and uses the same endpoint as other graded content.

**Settings.** `maxAttempts` (`0` = unlimited) can be changed at any time with
`PUT /quizzes/{quizId}/settings`. Result visibility is changed with
`PUT /quizzes/{quizId}/result-visibility`, and the `deadline` through
`PUT /contents/{contentId}/submission-policy`. A quiz closes at its deadline: no new attempts after
it (exactly at the deadline is fine). All settings, plus `published`, `closed`,
`showResultsImmediately` and `resultsPublished`, come back on `ContentResponse` from
`GET /contents/{contentId}`.

**Hints.** Hints are written per question; there is no quiz-wide switch. A student sees the hint of
each question their latest attempt got wrong, together with the results, so a hint never reveals
which answers were wrong before the results are visible.

**Question types.** `type` is `CHOICE` (the default when left out), `ORDERING`, `MATCHING` or
`FILL_IN`. Each question is worth one point and is right or wrong as a whole: there is no partial
credit.

| `type` | How it is authored | How a student answers | Correct when |
| --- | --- | --- | --- |
| `CHOICE` | Options with `correct` flags. One correct option makes it single choice, several make it multiple choice. | Selects options. | Exactly the correct options are selected, no more and no fewer. |
| `ORDERING` | Options listed in the correct order; `correct` is ignored. | Puts every option in order. Options are served in a random order, which is sometimes the correct one, so the order gives nothing away. | The submitted list is exactly the authored order. |
| `MATCHING` | Pairs: each option's `text` is the left-hand item and `matchText` its partner. | Chooses a right-hand item for every left-hand item. Left-hand items are served in order, right-hand items (`matches`) shuffled. | Every chosen right-hand item has the text that belongs to its left-hand item. Pairs with the same right-hand text are interchangeable. |
| `FILL_IN` | The question `text` has one `___` per blank. Each option holds one blank's accepted answers, separated by `|` (for example `Norway|Norge`). | Types an answer for each blank. | Every blank matches one of its accepted answers, ignoring capital letters and extra spaces. |

Submissions carry two maps keyed by question id, and either may be left out:
`answers` has option ids (`CHOICE`: the selected ones; `ORDERING`: every option in the chosen order;
`MATCHING`: one right-hand id per left-hand item, in order) and `textAnswers` has the typed answer
for each blank (`FILL_IN`, at most 20 blanks and 200 characters each). An unanswered question, or an
answer with the wrong number of entries, is simply wrong.

Students never receive the answers. For `ORDERING` and `MATCHING` the answer is the order the teacher
typed, and database ids follow creation order, so the options are created in a random order and the
teacher's order is kept in a separate `position`. Sorting the ids a student receives therefore
reveals nothing. `FILL_IN` options are not sent to students at all.

Quiz-related endpoints (all under `/api`, teacher-only writes unless noted):

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/modules/{moduleId}/quizzes` | Create a quiz: `title`, `maxAttempts`, optional `deadline` and `showResultsImmediately` (teacher) |
| `PUT` | `/quizzes/{quizId}/settings` | Change `maxAttempts`, at any time (teacher) |
| `PUT` | `/quizzes/{quizId}/result-visibility` | Show results as soon as attempts are graded, or only once published (teacher) |
| `POST` | `/quizzes/{quizId}/questions` | Add a question: `text`, optional `hint`, `type`, `options` with `text`, `correct`, `matchText` (teacher, draft only) |
| `PUT` | `/quizzes/{quizId}/questions/{questionId}` | Replace a question, including its type (teacher, draft only) |
| `DELETE` | `/quizzes/{quizId}/questions/{questionId}` | Delete a question (teacher, draft only) |
| `PUT` | `/quizzes/{quizId}/publication` | Publish the quiz to students and freeze its questions (teacher) |
| `GET` | `/quizzes/{quizId}/questions` | List questions without answers or hints; `ORDERING` options and `MATCHING` right-hand items (`matches`) are shuffled (any enrolled member) |
| `GET` | `/quizzes/{quizId}/questions/with-answers` | List questions with answers and hints, options in the order entered (teacher) |
| `POST` | `/quizzes/{quizId}/submissions` | Submit `answers` and/or `textAnswers` for grading; returns 202, grading happens async (any enrolled member) |
| `GET` | `/quizzes/{quizId}/attempts/mine` | Own attempts: `attemptsUsed`, `attemptsGraded`, `resultsVisible`, and `best`, `latest` and `hints` (empty while results are hidden) |
| `GET` | `/quizzes/{quizId}/grade` | Own best grade; 404 until it is graded and visible to students |
| `GET` | `/quizzes/{quizId}/results` | Every student's attempts and best grade, always visible (teacher) |
| `PUT` | `/contents/{contentId}/grades/publication` | Publish the grades so students see them when results are not immediate (teacher) |

## Assignment and coding-exercise deadlines

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

## File hand-ins

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

## AI tutor

A floating chat panel for students that streams answers from Gemini. All model calls go through the
backend; the API key is a server-side secret and never reaches the browser.

- **Switch.** `AI_TUTOR_ENABLED=true` (with a `GEMINI_API_KEY`) turns it on for every course. Only
  students enrolled in a course see it, and on the homepage it appears for anyone who is a student in
  at least one course. Teachers do not see it.
- **What it knows.** Only whether the student is inside a course, and that course's name, description
  and the pages the student can currently see (unlocked and inside their visibility window). It never sees assignments, quizzes, deadlines, submissions or grades.
- **Behavior.** It explains, gives hints and asks guiding questions, and does not hand out complete
  solutions to graded work. Course material is escaped and treated as data, not instructions.
- **API.** `GET /api/ai-tutor/config` tells the widget whether to show itself. `POST /api/ai-tutor/chat`
  streams the answer as server-sent events (`delta`, then `done`, or `error`).
- **Limits.** Each request is checked for enrollment, and each user is rate limited (429 with
  `Retry-After`). The limit is kept in memory, so it is per application instance.

## Deployment and CI

The `Dockerfile` is a self-contained multi-stage build: it builds the frontend, bundles it into the
Spring Boot jar, and produces one runtime image that defaults to the `prod` profile. No local Maven or
Node build is needed first. `compose.yaml` runs the `app` service (profile `prod`) next to RabbitMQ and
Redis and forwards the OAuth, Gemini and AI tutor settings from `.env`.

Only `/api/**` and `/actuator/**` require login; everything else is the public frontend, which has to
load before sign-in so it can show the sign-in links.

GitHub Actions (`.github/workflows/ci.yml`) runs on pushes to the default branch and on pull requests.
The `frontend` job runs lint, tests and build; the `backend` job runs `./mvnw test`; the `docker` job then
builds the image. On the default branch only, it pushes the image to the GitHub Container Registry as
`ghcr.io/<owner>/<repo>/app` (tags `latest` and the commit hash). That matches the Compose image
`${IMAGE_REGISTRY}/app:latest`, so a host deploys it with `IMAGE_REGISTRY` set to `ghcr.io/<owner>/<repo>`.
CodeQL (`.github/workflows/codeql.yml`) scans the Java and TypeScript code, and Dependabot proposes
dependency updates.

## Testing

```
./mvnw test              # backend   (Windows: .\mvnw.cmd test)
cd frontend && npm test  # frontend  (Windows: npm.cmd test)
```

Run `npm run lint` and `npm run build` in `frontend/` before opening a pull request. The frontend
README covers testing, folder structure and the Vite proxy in more detail.

Backend tests cover the repositories (users, courses, enrollments, modules, groups, memberships), the
services and controllers, and the quiz grading logic. `H2DatabaseTests` starts the actual web
application using the `h2console` profile (not `dev`), saves and reloads all four content types and
student interactions, and checks console access and API authentication.

`QuizGradingPipelineIntegrationTests` is the one exception: it needs a local Docker daemon, since it
uses Testcontainers to start disposable RabbitMQ and Redis containers and prove the submission-grading
pipeline against real infrastructure. GitHub-hosted runners provide Docker, so CI runs this test along with the rest. To run it alone, use
`./mvnw test -Dtest=QuizGradingPipelineIntegrationTests`.
`TutorLiveSmokeTests` calls the real Gemini API and only runs with `RUN_LIVE_AI_TESTS=true`.
