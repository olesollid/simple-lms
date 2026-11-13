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
The dev profile disables automatic Docker Compose startup; Redis and RabbitMQ
are not required for these database operations.

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
H2DatabaseTests starts the actual web application using the dev datasource,
saves and reloads all four content types and student interactions, and checks
console access and API authentication. Tests do not need external services.

These defaults are for development, not deployment. To retain data across restarts,
configure a file URL such as `jdbc:h2:file:./data/canvas2;DB_CLOSE_ON_EXIT=FALSE`
and change `ddl-auto` to `update` for local development; changing the URL alone
does not prevent create-drop from deleting data. For deployment, configure an
appropriate datasource and managed schema migrations, and leave the dev profile off.

### Java Spring template project

This project is based on a GitLab [Project Template](https://docs.gitlab.com/ee/gitlab-basics/create-project.html).

Improvements can be proposed in the [original project](https://gitlab.com/gitlab-org/project-templates/spring).

### CI/CD with Auto DevOps

This template is compatible with [Auto DevOps](https://docs.gitlab.com/ee/topics/autodevops/).

If Auto DevOps is not already enabled for this project, you can [turn it on](https://docs.gitlab.com/ee/topics/autodevops/#enabling-auto-devops) in the project settings.
