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

`application.properties` defines the in-memory datasource. Hibernate's
`ddl-auto=create-drop` creates tables at startup and drops them at shutdown;
schema errors fail startup. All data is disposable and lost on restart.
`open-in-view=false` keeps database work out of web response rendering.

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

These defaults are for development, not deployment. To retain data across restarts,
configure a file URL such as `jdbc:h2:file:./data/canvas2;DB_CLOSE_ON_EXIT=FALSE`
and change `ddl-auto` to `update` for local development; changing the URL alone
does not prevent create-drop from deleting data. For deployment, configure an
appropriate datasource and managed schema migrations, and leave the dev profile off.

### Quiz submission grading

Quiz submissions are graded asynchronously: a student's answers are published
as an event to RabbitMQ, a listener consumes it, grades it, and stores the
result in Redis. This only runs under the `dev` profile -- `SubmissionGradingListener`
is `@Profile("dev")`-gated specifically so the rest of the test suite, CI, and
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

Two separate profiles exist for a reason: `dev` is the full app (H2 console +
quiz grading, needs a broker); `h2console` is H2-console-only, used by
`H2DatabaseTests` so that test doesn't need RabbitMQ or Redis at all.

Quiz-related endpoints (all under `/api`, teacher-only writes unless noted):

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/modules/{moduleId}/quizzes` | Create a quiz (teacher) |
| `POST` | `/quizzes/{quizId}/questions` | Add a question with answer options, supports multi-select (teacher) |
| `GET` | `/quizzes/{quizId}/questions` | List questions without correct-answer flags (any enrolled member) |
| `GET` | `/quizzes/{quizId}/questions/with-answers` | List questions including correct-answer flags (teacher) |
| `POST` | `/quizzes/{quizId}/submissions` | Submit answers for grading; returns 202, grading happens async (any enrolled member) |
| `GET` | `/quizzes/{quizId}/grade` | Read the caller's own grade; 404 until grading completes (any enrolled member) |

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
