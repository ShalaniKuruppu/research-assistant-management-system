# Research Assistant Management System

A supervisor uses this system to run research projects and the assistants who work on them. The supervisor creates a project, assigns research assistants, sets tasks, and reviews the weekly report each assistant submits. A research assistant sees only the projects they belong to, updates task progress, and files one weekly report per project.

There are two roles. An account is either a supervisor or a research assistant, and that role stays fixed.

## What each role does

**Supervisor**

- Creates and owns projects
- Assigns any number of research assistants to a project
- Creates tasks and assigns each task to an assistant on that project
- Reads task progress
- Reviews submitted weekly reports and records an outcome: approved or needs revision

**Research assistant**

- Works on the projects a supervisor has assigned
- Updates the status and completion of assigned tasks, including blockers
- Saves a weekly report as a draft, then submits it
- Reads the supervisor's feedback

## Architecture

The system is a three-tier application. The browser talks only to the API. The API is the only tier that talks to the database.

```text
React  →  Spring Boot  →  PostgreSQL
UI         REST API         data
```

| Tier | Technology | Responsibility |
|---|---|---|
| Presentation | React | Screens for the supervisor and the research assistant. Sign-in, projects, assignments, tasks, weekly reports, and feedback. |
| Application | Spring Boot | REST API, authentication, and role checks. Enforces who may assign assistants, create tasks, post progress, submit reports, and leave feedback. |
| Data | PostgreSQL | Stores users, projects, assignments, tasks, progress, weekly reports, and feedback. Constraints and triggers protect the rules below. |

A request follows one path. React sends JSON to a Spring Boot endpoint. Spring Boot checks the signed-in user's role, applies the project rules, and reads or writes PostgreSQL. The response goes back to React for display.

Passwords are stored as hashes. The database rejects a short plaintext value in `password_hash`.

## Tech stack

| Layer | Choice |
|---|---|
| Frontend | React |
| Backend | Spring Boot |
| Database | PostgreSQL 14 or newer |
| API style | REST, JSON |
| Schema | `db/schema.sql` |

PostgreSQL holds the durable data and the hard rules: one role per person, one assignment of an assistant per project, one weekly report per assistant per project week, and task progress taken from the latest update. Spring Boot is where sign-in and the role-specific actions live. React renders the two workspaces and never opens a database connection.

## Domain model

The entity relationship diagram is in [docs/ER Diagram.png](docs/ER%20Diagram.png).

| Table | Purpose |
|---|---|
| `users` | Supervisors and research assistants |
| `projects` | A project owned by one supervisor |
| `project_assistants` | One research assistant assigned to one project |
| `tasks` | Work created by the supervisor and assigned to an assistant |
| `task_updates` | Progress log for a task |
| `weekly_reports` | One assistant's report for one project week |
| `report_feedback` | The supervisor's review of a submitted report |

`tasks.status` and `tasks.completion_percentage` are the current snapshot. They change when a `task_updates` row is added.

### Rules

- A project can have many research assistants. The same assistant is assigned to that project only once.
- The supervisor who owns the project assigns those assistants.
- A task assignee must already be an active research assistant on that project.
- Only that project's supervisor can create its tasks.
- Progress notes can be written by the assignee or that project's supervisor.
- A blocked update must include a blocker note. Completion percentage is from 0 to 100.
- An assistant files one weekly report per project per week. A draft can be saved before it is submitted.
- Feedback can be written only by that project's supervisor, and only after the report leaves draft. The first feedback row marks the report reviewed.
- People are deactivated with `is_active`. A project is retired with status `ARCHIVED`. Rows are kept so history stays.

### Allowed values

| Field | Values |
|---|---|
| `users.role` | `SUPERVISOR`, `RESEARCH_ASSISTANT` |
| `projects.status` | `PLANNED`, `ACTIVE`, `ON_HOLD`, `COMPLETED`, `ARCHIVED` |
| `tasks.priority` | `LOW`, `MEDIUM`, `HIGH` |
| `tasks.status` and `task_updates.status` | `TODO`, `IN_PROGRESS`, `BLOCKED`, `DONE`, `CANCELLED` |
| `weekly_reports.status` | `DRAFT`, `SUBMITTED`, `REVIEWED` |
| `report_feedback.outcome` | `APPROVED`, `NEEDS_REVISION` |

`CANCELLED` means the task was dropped. `DONE` means the work finished.

## Repository

```text
db/schema.sql          PostgreSQL schema
docs/ER Diagram.png    Entity relationship diagram
docker-compose.yml     Local PostgreSQL for the API
backend/               Spring Boot REST API
```

The React frontend is not in this repository yet.

## Database setup

Start PostgreSQL with Docker. The compose file creates the database and applies `db/schema.sql` on the first start:

```bash
docker compose up -d
```

The API defaults are database `research_assistant_management`, user `rams`, and password `rams` on `localhost:5433`. Host port 5433 is used so this database does not collide with a PostgreSQL server already listening on 5432.

To apply the schema yourself instead:

```bash
createdb research_assistant_management
psql -d research_assistant_management -f db/schema.sql
```

Apply `db/schema.sql` once, to an empty database. Running it again on a database that already has these tables will fail. The API does not create or alter tables.

## Backend

The API lives in `backend/`. It checks the signed-in user, then reads and writes PostgreSQL. Passwords are stored as bcrypt hashes. Sign-in returns a bearer token; send it as `Authorization: Bearer <token>`.

From the repository root, with the database already running:

```bash
cd backend
mvn spring-boot:run
```

The API listens on `http://localhost:8080`. Set `JWT_SECRET` to a private value of at least 32 bytes before using this anywhere other than a local machine. `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD` override the database connection.

| Method | Path | Who |
|---|---|---|
| POST | `/api/auth/register` | Public. Body: `name`, `email`, `password`, `role` (`SUPERVISOR` or `RESEARCH_ASSISTANT`). Role cannot be changed later. |
| POST | `/api/auth/login` | Public. Body: `email`, `password`. |
| GET | `/api/health` | Public. |
| GET | `/api/me` | Signed in. |
| GET | `/api/users?role=RESEARCH_ASSISTANT` | Supervisor. Active research assistants, for assignment. |
| POST | `/api/projects` | Supervisor. |
| GET | `/api/projects` | Supervisor sees owned projects. An assistant sees assigned projects. |
| GET | `/api/projects/{id}` | Owner or assigned assistant. |
| PATCH | `/api/projects/{id}` | Owner. Send only the fields to change. |
| POST | `/api/projects/{id}/assistants` | Owner. Body: `assistantId`. |
| GET | `/api/projects/{id}/assistants` | Owner or assigned assistant. |
| PATCH | `/api/projects/{id}/assistants/{assistantId}` | Owner. Body: `active`. |
| POST | `/api/projects/{id}/tasks` | Owner. |
| GET | `/api/projects/{id}/tasks` | Owner sees every task. An assistant sees assigned tasks. |
| GET | `/api/tasks/{id}` | Owner or assignee. |
| PATCH | `/api/tasks/{id}` | Owner. Does not change status or completion. |
| POST | `/api/tasks/{id}/updates` | Assignee or owner. This is how status and completion change. A `BLOCKED` update needs `blockerNote`. |
| GET | `/api/tasks/{id}/updates` | Assignee or owner. |
| POST | `/api/projects/{id}/reports` | Assigned assistant. Saves a draft. |
| GET | `/api/projects/{id}/reports` | Owner sees every report. An assistant sees their own. |
| GET | `/api/reports/{id}` | Owner or author. |
| PATCH | `/api/reports/{id}` | Author, while the report is a draft or the latest feedback is `NEEDS_REVISION`. |
| POST | `/api/reports/{id}/submit` | Author, and only from draft. `workCompleted` is required. |
| POST | `/api/reports/{id}/feedback` | Owner, after the report leaves draft. Body: `feedbackText`, `outcome` (`APPROVED` or `NEEDS_REVISION`). |
| GET | `/api/reports/{id}/feedback` | Owner or author. |

Errors are JSON: `status`, `error`, and `message`. A project, task, or report the caller cannot see is `404`. An action the caller can see but is not allowed to take is `403`.
