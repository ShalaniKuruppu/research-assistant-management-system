# Research Assistant Management System

A supervisor uses this system to run research projects and the assistants who work on them. A research assistant creates a project under a chosen supervisor, then updates task progress and files one weekly report per project. The creating research assistant can add more assistants to that project. The supervisor owns the project, sets tasks, and reviews weekly reports.

There are two roles. An account is either a supervisor or a research assistant, and that role stays fixed.

## What each role does

**Supervisor**

- Owns projects created under them by research assistants
- Creates tasks and assigns each task to an assistant on that project
- Reads task progress, research plans, and Overleaf paper links
- Reviews submitted weekly reports and records an outcome: approved or needs revision
- Reviews reimbursement claims on owned projects and records approved or rejected
- Browses the research assistant directory and can create assistant accounts
- Browses development projects filed by research assistants

**Research assistant**

- Creates projects and chooses the supervising account
- Adds other research assistants only to projects they created
- Works on the projects they create or are assigned to
- Updates the status and completion of assigned tasks, including blockers
- Saves a research plan and an Overleaf paper link on assigned projects
- Saves a weekly report as a draft, then submits it
- Reads the supervisor's feedback
- Files reimbursement claims with receipt uploads on assigned projects
- Tracks separate development projects (not tied to a research project)
- Updates their profile (email, phone, summary, picture)

## Architecture

The system is a three-tier application. The browser talks only to the API. The API is the only tier that talks to the database.

```text
React  →  Spring Boot  →  PostgreSQL
UI         REST API         data
```

| Tier | Technology | Responsibility |
|---|---|---|
| Presentation | React | Screens for the supervisor and the research assistant. Sign-in, the dashboard, projects, assignments, tasks, weekly reports, feedback, reimbursements, development projects, assistant directory, and profile. |
| Application | Spring Boot | REST API, authentication, and role checks. Enforces who may assign assistants, create tasks, post progress, submit reports, leave feedback, file reimbursements, and manage development projects. |
| Data | PostgreSQL | Stores users, projects, assignments, tasks, progress, weekly reports, feedback, reimbursements, receipts, research plan history, and development projects. Constraints and triggers protect the rules below. |

A request follows one path. React sends JSON to a Spring Boot endpoint. Spring Boot checks the signed-in user's role, applies the project rules, and reads or writes PostgreSQL. The response goes back to React for display.

Passwords are stored as hashes. The database rejects a short plaintext value in `password_hash`.

## Tech stack

| Layer | Choice |
|---|---|
| Frontend | React 19, React Router, Vite |
| Backend | Spring Boot 3.4, Java 17, Maven |
| Database | PostgreSQL 16 in Docker, or PostgreSQL 14 or newer if you install it yourself |
| Auth | JWT bearer tokens. Passwords are bcrypt hashes. A token lasts 12 hours unless `JWT_EXPIRATION_MINUTES` says otherwise. |
| Uploads | Profile pictures and reimbursement receipts under `uploads/` (override with `RAMS_UPLOAD_DIR`) |
| API style | REST, JSON |
| Schema | `db/schema.sql` |

PostgreSQL holds the durable data and the hard rules: one role per person, one assignment of an assistant per project, one weekly report per assistant per project week, and task progress taken from the latest update. Spring Boot is where sign-in and the role-specific actions live. React renders the two workspaces and never opens a database connection.

## Run locally

Start the three tiers in this order. Docker, Java 17, Maven, and Node.js are required.

```bash
docker compose up -d
cd backend
mvn spring-boot:run
```

In a second terminal, with the API already listening:

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`. The dev server forwards `/api` to `http://localhost:8080`. The API defaults are database `research_assistant_management`, user `rams`, and password `rams` on `localhost:5433`. Host port 5433 is used so this database does not collide with a PostgreSQL server already listening on 5432.

Set `JWT_SECRET` to a private value of at least 32 bytes before using this anywhere other than a local machine. `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD` override the database connection.

## Project extras

An assigned research assistant can submit an HTTPS Overleaf project link from
the project workspace. The project supervisor can then open the submitted link
from the project page. RAMS does not bypass Overleaf permissions; the viewer
must have access to the Overleaf project.

An assigned research assistant can also write and update a research plan from
the project workspace. The project supervisor can read the saved plan.
The system preserves the previous plan on every changed update and both users
can expand the timestamped previous versions.

Assigned assistants can draft reimbursement claims, attach receipt files, and
submit them. The project supervisor reviews each claim as approved or rejected.

Research assistants track development work on `/development-projects` as a
separate activity from research projects. Supervisors can browse those entries;
only the owning assistant can create or update them.

## Screens

| Path | Who | What |
|---|---|---|
| `/sign-in` | Guest | Sign in with email and password. |
| `/register` | Guest | Create an account. The role is chosen here and stays fixed. Password is 8 to 72 characters. |
| `/` | Signed in | Dashboard. |
| `/projects` | Signed in | Projects the caller can see. |
| `/projects/new` | Research assistant | Create a project under a chosen supervisor. |
| `/projects/:id` | Owner or assigned assistant | Project workspace (tasks, reports, plan, paper link, reimbursements, assistants). |
| `/development-projects` | Signed in | Standalone development activities. Assistants create and update their own; supervisors browse. |
| `/research-assistants` | Supervisor | Assistant directory with summary, contact, photo, and allocated projects. |
| `/profile` | Signed in | Update email, contact number, summary, and profile picture. |

The supervisor workspace creates tasks, reads progress, and reviews submitted weekly reports and reimbursement claims. The assistant who created a project can add other research assistants to it. The assistant workspace updates assigned tasks, saves and submits a weekly report, manages reimbursements, and reads the supervisor's feedback. A dashboard item opens the matching task, report, or claim on that project.

## Domain model

The entity relationship diagram is in [docs/ER Diagram.png](docs/ER%20Diagram.png).

| Table | Purpose |
|---|---|
| `users` | Supervisors and research assistants, including optional phone, summary, and profile image |
| `projects` | A project created by one research assistant and owned by one supervisor; may store paper link and research plan |
| `research_plan_versions` | Previous research plan text kept when the plan changes |
| `project_assistants` | One research assistant assigned to one project by that project's creator |
| `tasks` | Work created by the supervisor and assigned to an assistant |
| `task_updates` | Progress log for a task |
| `weekly_reports` | One assistant's report for one project week |
| `report_feedback` | The supervisor's review of a submitted report |
| `reimbursements` | One assistant's expense claim on one project |
| `reimbursement_receipts` | Files attached to a reimbursement claim |
| `development_projects` | Standalone development work owned by a research assistant |

`tasks.status` and `tasks.completion_percentage` are the current snapshot. They change when a `task_updates` row is added.

### Rules

- A project can have many research assistants. The same assistant is assigned to that project only once.
- Only the research assistant who created the project can add or remove assistants on it.
- A task assignee must already be an active research assistant on that project.
- Only that project's supervisor can create its tasks.
- Progress notes can be written by the assignee or that project's supervisor.
- A blocked update must include a blocker note. Completion percentage is from 0 to 100.
- An assistant files one weekly report per project per week. A draft can be saved before it is submitted.
- Feedback can be written only by that project's supervisor, and only after the report leaves draft. The first feedback row marks the report reviewed.
- Reimbursement claims can be filed only by an active assistant on that project. Drafts can be edited and receive receipt uploads before submit. Only the project supervisor reviews a submitted claim.
- Development projects are not tied to a research project. Only a research assistant can create one; only the owner can update it. Supervisors can read all development projects.
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
| `reimbursements.status` | `DRAFT`, `SUBMITTED`, `APPROVED`, `REJECTED` |
| `development_projects.status` | `PLANNED`, `IN_PROGRESS`, `ON_HOLD`, `COMPLETED` |

`CANCELLED` means the task was dropped. `DONE` means the work finished.

## Dashboard

`GET /api/dashboard` returns counts, one summary per visible project, and a list of items that need attention. A supervisor sees owned projects. An assistant sees assigned projects and only their own tasks and reports.

The home screen shows eight counts: total projects, ongoing (`ACTIVE`), blocked (`ON_HOLD`), reimbursements (claims on visible projects), overdue projects (end date before today and not completed/archived), pending (`PLANNED`), completed projects, and number of active research assistant accounts. Overdue can overlap other status counts, so the eight numbers are not meant to sum to total. The dashboard refetches when you open or refocus the page, and every 30 seconds while the tab is visible. Each project summary shows open tasks, blocked tasks, and average completion of active tasks. Open means the task is active and neither `DONE` nor `CANCELLED`. Overdue tasks mean an open task whose due date is before today.

The attention list is ordered as follows. Each role sees the kinds in its column.

| Kind | Who sees it | Meaning |
|---|---|---|
| `REVIEW` | Supervisor | A report is `SUBMITTED` and waiting for feedback. |
| `REIMBURSEMENT_REVIEW` | Supervisor | A reimbursement is `SUBMITTED` and waiting for review. |
| `REVISION` | Research assistant | The latest feedback on their report is `NEEDS_REVISION`. |
| `REIMBURSEMENT_REJECTED` | Research assistant | Their reimbursement was `REJECTED`. |
| `BLOCKED` | Both | An active task is `BLOCKED`. |
| `OVERDUE` | Both | An open task is past its due date. |
| `DRAFT` | Research assistant | Their report is still a draft. |
| `MISSING_REPORT` | Both, on an `ACTIVE` project | No report for that person covers today. A supervisor sees one item per active assistant. |

The response also counts submitted reports, drafts, and reports whose latest feedback is `NEEDS_REVISION`.

## Repository

```text
db/schema.sql          PostgreSQL schema
db/migrations/         Incremental SQL for existing databases
docs/ER Diagram.png    Entity relationship diagram
docker-compose.yml     Local PostgreSQL for the API
backend/               Spring Boot REST API
frontend/              React workspace
```

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

For an existing database that was created from an older schema, apply the migrations in order:

```bash
psql -h localhost -p 5433 -U rams -d research_assistant_management -f db/migrations/003_project_paper_link.sql
psql -h localhost -p 5433 -U rams -d research_assistant_management -f db/migrations/004_research_plan.sql
psql -h localhost -p 5433 -U rams -d research_assistant_management -f db/migrations/005_research_plan_history.sql
psql -h localhost -p 5433 -U rams -d research_assistant_management -f db/migrations/006_user_profile_fields.sql
psql -h localhost -p 5433 -U rams -d research_assistant_management -f db/migrations/007_reimbursements.sql
psql -h localhost -p 5433 -U rams -d research_assistant_management -f db/migrations/008_development_projects.sql
psql -h localhost -p 5433 -U rams -d research_assistant_management -f db/migrations/009_development_projects_standalone.sql
psql -h localhost -p 5433 -U rams -d research_assistant_management -f db/migrations/010_project_created_by.sql
```

Fresh installs that use `db/schema.sql` already include these changes and do not need the migration files.

## Backend

The API lives in `backend/`. It checks the signed-in user, then reads and writes PostgreSQL. Passwords are stored as bcrypt hashes. Sign-in returns a bearer token; send it as `Authorization: Bearer <token>`.

From the repository root, with the database already running:

```bash
cd backend
mvn spring-boot:run
```

The API listens on `http://localhost:8080`.

API tests use Testcontainers and need Docker:

```bash
cd backend
mvn test
```

| Method | Path | Who |
|---|---|---|
| POST | `/api/auth/register` | Public. Body: `name`, `email`, `password` (8 to 72 characters), `role` (`SUPERVISOR` or `RESEARCH_ASSISTANT`). Role cannot be changed later. |
| POST | `/api/auth/login` | Public. Body: `email`, `password`. |
| GET | `/api/health` | Public. |
| GET | `/api/me` | Signed in. |
| PATCH | `/api/me` | Signed in. Update profile fields such as email, phone, and summary. |
| POST | `/api/me/profile-picture` | Signed in. Multipart file upload. |
| GET | `/api/dashboard` | Signed in. Counts, project summaries, and the attention list described above. |
| GET | `/api/users?role=RESEARCH_ASSISTANT` | Supervisor or research assistant. Active research assistants, for assignment. |
| GET | `/api/users?role=SUPERVISOR` | Research assistant. Active supervisors, for project creation. |
| GET | `/api/research-assistants` | Supervisor. Directory profiles with allocated projects. |
| POST | `/api/research-assistants` | Supervisor. Create a research assistant account. |
| POST | `/api/projects` | Research assistant. Body includes `supervisorId`. Creator is auto-assigned. |
| GET | `/api/projects` | Supervisor sees owned projects. An assistant sees assigned projects. |
| GET | `/api/projects/{id}` | Owner or assigned assistant. |
| PATCH | `/api/projects/{id}` | Owner. Send only the fields to change. |
| PATCH | `/api/projects/{id}/paper-link` | Assigned assistant. Body: `paperLink`. |
| PATCH | `/api/projects/{id}/research-plan` | Assigned assistant. Body: `researchPlan`. Previous text is kept in history. |
| GET | `/api/projects/{id}/research-plan/history` | Owner or assigned assistant. |
| POST | `/api/projects/{id}/assistants` | Creating research assistant. Body: `assistantId`. |
| GET | `/api/projects/{id}/assistants` | Owner or assigned assistant. |
| PATCH | `/api/projects/{id}/assistants/{assistantId}` | Creating research assistant. Body: `active`. |
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
| POST | `/api/projects/{id}/reimbursements` | Assigned assistant. Creates a draft claim. |
| GET | `/api/projects/{id}/reimbursements` | Owner or assigned assistant. |
| GET | `/api/reimbursements/{id}` | Owner or claim author. |
| PATCH | `/api/reimbursements/{id}` | Author, while the claim is a draft. |
| POST | `/api/reimbursements/{id}/receipts` | Author, while draft. Multipart file upload. |
| DELETE | `/api/reimbursements/{id}/receipts/{receiptId}` | Author, while draft. |
| GET | `/api/reimbursements/{id}/receipts/{receiptId}/file` | Owner or claim author. |
| POST | `/api/reimbursements/{id}/submit` | Author, from draft. |
| POST | `/api/reimbursements/{id}/review` | Owner, after submit. Body: `status` (`APPROVED` or `REJECTED`), optional `reviewerNote`. |
| GET | `/api/development-projects` | Signed in. Assistants see their own; supervisors see all. |
| POST | `/api/development-projects` | Research assistant. |
| GET | `/api/development-projects/{id}` | Owner or any supervisor. |
| PATCH | `/api/development-projects/{id}` | Owning research assistant. |

Errors are JSON: `status`, `error`, and `message`. A project, task, or report the caller cannot see is `404`. An action the caller can see but is not allowed to take is `403`.
