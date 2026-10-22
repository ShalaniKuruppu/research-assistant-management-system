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

The running system has three parts: a React frontend, a Spring Boot backend, and the PostgreSQL database. The schema and diagram in this repository are the data tier.

```text
db/schema.sql          PostgreSQL schema
docs/ER Diagram.png    Entity relationship diagram
```

## Database setup

Create an empty database and apply the schema:

```bash
createdb research_assistant_management
psql -d research_assistant_management -f db/schema.sql
```

Apply `db/schema.sql` once, to an empty database. Running it again on a database that already has these tables will fail.
