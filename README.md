# Canvas 2.0

A learning management system built for the course DAT250 at HVL. Teachers create courses with modules
and content (pages, quizzes, assignments and coding exercises) and enroll students. Students work
through the material, take auto-graded quizzes, hand in assignments alone or in groups, and follow their
grades. Users sign in with GitHub or Google, and roles are per course. An optional AI study tutor,
powered by Google Gemini, can answer students' questions about their course.

Built with Java 25 and Spring Boot on the backend and React with TypeScript on the frontend, using H2,
RabbitMQ and Redis, and packaged with Docker. See [TECHNICAL.md](TECHNICAL.md) for the stack and the
details.

## Before you start

| To run | You need |
| --- | --- |
| Development | JDK 25, Node.js 22.22.2, Docker (for RabbitMQ and Redis) |
| Production | Docker only (Docker Desktop on macOS and Windows; start it first) |

### Set up `.env`

Create a file named `.env` in the project root, an example exists in `.env.example`. It is git-ignored, so your secrets stay local.

```
export GITHUB_CLIENT_ID=your-github-client-id
export GITHUB_CLIENT_SECRET=your-github-client-secret
export GOOGLE_CLIENT_ID=your-google-client-id
export GOOGLE_CLIENT_SECRET=your-google-client-secret

# Novel feature: the AI tutor
export AI_TUTOR_ENABLED=false
export GEMINI_API_KEY=your-gemini-api-key
```

The group's real values are stored as CI/CD variables in the GitLab project (**Settings → CI/CD →
Variables**). GitLab only shows these to project Maintainers, so if you can't see them, ask the group,
or create your own OAuth apps as described below.

- To use your own OAuth apps, create a GitHub OAuth app and a Google OAuth client and allow these
  callback URLs: `http://localhost:8080/login/oauth2/code/github` and
  `http://localhost:8080/login/oauth2/code/google`. The same URLs work in development: the Vite dev
  server forwards sign-in to the backend on port 8080, which builds the callback from its own address.

- Any non-empty text is enough to start the app, but you need real values to sign in.
- To try the AI tutor, set `AI_TUTOR_ENABLED=true` and use a real `GEMINI_API_KEY`.
- To see both sides of a course, sign in with two accounts: the creator of a course is its teacher,
  and teachers do not see the AI tutor.

## Run in development

You need three things running: RabbitMQ and Redis in Docker, the backend, and the frontend. Open a
terminal for each of the last two, in the project root. Development data lives in memory and is lost
when the backend stops.

**macOS**

```bash
# 1. RabbitMQ and Redis
docker compose up rabbitmq redis -d

# 2. Backend (loads .env into this terminal first)
set -a; source .env; set +a
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 3. Frontend (in a second terminal)
cd frontend
npm ci
npm run dev
```

**Windows (PowerShell)**

```powershell
# 1. RabbitMQ and Redis
docker compose up rabbitmq redis -d

# 2. Backend (loads .env into this PowerShell window first)
Get-Content .env | ForEach-Object {
  if ($_ -match '^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') {
    Set-Item -Path "Env:$($Matches[1])" -Value $Matches[2].Trim().Trim('"').Trim("'")
  }
}
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"

# 3. Frontend (in a second PowerShell window)
cd frontend
npm.cmd ci
npm.cmd run dev
```

Open **http://localhost:5173** and sign in.

To stop, press `Ctrl+C` in the backend and frontend terminals and run `docker compose down`.

## Run in production

Production runs everything in Docker: the app (with the frontend built in), RabbitMQ and Redis. The
command is the same on macOS and Windows, and Docker Compose reads `.env` by itself.

```
docker compose --profile prod up -d --build
```

Open **http://localhost:8080** and sign in. The database is kept in a Docker volume, so your data
survives restarts and rebuilds.

- **After code changes**, run the same command again. It rebuilds the image and replaces the app
  container.
- **To stop** and keep the data: `docker compose --profile prod down`
- **To stop and delete the data**: `docker compose --profile prod down -v`
- The app uses port 8080, so stop it before running the backend in development, and the other way round.

### Deploy to a host

On every push to `master`, GitLab CI builds the image and pushes it to the project's container registry.
A server with Docker can run that image instead of building it. With `compose.yaml` and `.env` in place:

```
docker login git.app.uib.no:4567
export D250_REG=git.app.uib.no:4567/hvl-dat-250/26h/semester-project/group-9/canvas-v2
docker compose --profile prod pull app
docker compose --profile prod up -d --no-build
```

Log in with your GitLab username and an access token with the `read_registry` scope. For any address other
than `http://localhost:8080`, set `FRONTEND_URL` and register the matching OAuth callback URLs.

## Tests

| | macOS | Windows (PowerShell) |
| --- | --- | --- |
| Backend | `./mvnw test` | `.\mvnw.cmd test` |
| Frontend | `cd frontend && npm test` | `cd frontend; npm.cmd test` |

Docker must be running for the backend tests: `QuizGradingPipelineIntegrationTests` starts its own
RabbitMQ and Redis containers, which can take a couple of minutes on a slow machine.

## More documentation

- [TECHNICAL.md](TECHNICAL.md): stack, configuration, persistence, quizzes, the AI tutor, deployment
  and CI.
- [frontend/README.md](frontend/README.md): the React app, its commands and folder structure.
