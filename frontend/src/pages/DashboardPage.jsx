import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, api } from '../api.js'
import { useAuth } from '../auth.jsx'
import { label } from '../format.js'
import Shell from '../Shell.jsx'

export default function DashboardPage() {
  const { user } = useAuth()
  const supervisor = user.role === 'SUPERVISOR'
  const [dashboard, setDashboard] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    api('/api/dashboard')
      .then((data) => {
        if (!cancelled) setDashboard(data)
      })
      .catch((caught) => {
        if (!cancelled) {
          setError(caught instanceof ApiError ? caught.message : 'Could not load the dashboard')
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  const stats = dashboard ? [
    ['Projects', dashboard.projectCount],
    ['Open tasks', dashboard.openTaskCount],
    ['Blocked', dashboard.blockedTaskCount],
    ['Overdue', dashboard.overdueTaskCount],
  ] : []

  return (
    <Shell>
      <div className="page-head">
        <div>
          <p className="eyebrow">Dashboard</p>
          <h1>{supervisor ? 'Your workspace' : 'Your work'}</h1>
          <p className="lede">
            {supervisor
              ? 'Projects you own, and the reports and tasks that need a decision.'
              : 'Assigned work, and what to do next.'}
          </p>
        </div>
        {supervisor ? <Link className="button primary" to="/projects/new">New project</Link> : null}
      </div>
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      {loading ? <p className="status-line">Loading the dashboard…</p> : null}
      {dashboard ? (
        <>
          <div className="stats">
            {stats.map(([name, value]) => (
              <article className="stat" key={name}>
                <strong>{value}</strong>
                <span>{name}</span>
              </article>
            ))}
          </div>
          <section className="split-layout">
            <div className="stack">
              <h2>Needs attention</h2>
              {dashboard.attention.length === 0 ? (
                <p className="lede">Nothing is waiting on you.</p>
              ) : null}
              {dashboard.attention.map((item) => (
                <Link className="list-card" key={attentionKey(item)} to={hrefFor(item)}>
                  <div>
                    <h2>{item.title}</h2>
                    <p className="meta">
                      {item.projectTitle}
                      {item.detail ? ` · ${item.detail}` : ''}
                    </p>
                  </div>
                  <span className="role">{attentionLabel(item.kind)}</span>
                </Link>
              ))}
            </div>
            <div className="stack">
              <div className="page-head">
                <h2>Projects</h2>
                <Link to="/projects">All projects</Link>
              </div>
              {dashboard.projects.length === 0 ? (
                <p className="lede">
                  {supervisor
                    ? 'Create a project to assign assistants and tasks.'
                    : 'A supervisor has not assigned you to a project yet.'}
                </p>
              ) : null}
              {dashboard.projects.map((project) => (
                <Link className="list-card" key={project.id} to={`/projects/${project.id}`}>
                  <div>
                    <h2>{project.title}</h2>
                    <p className="meta">
                      {project.openTaskCount} open
                      {project.blockedTaskCount ? ` · ${project.blockedTaskCount} blocked` : ''}
                      {` · ${project.averageCompletion}% complete`}
                    </p>
                    <p className="meta">
                      {supervisor
                        ? `${project.assistantCount} ${project.assistantCount === 1 ? 'assistant' : 'assistants'}`
                        : project.supervisorName}
                    </p>
                    <div className="meter" aria-hidden="true">
                      <span style={{ width: `${project.averageCompletion}%` }} />
                    </div>
                  </div>
                  <span className="role">{label(project.status)}</span>
                </Link>
              ))}
            </div>
          </section>
        </>
      ) : null}
    </Shell>
  )
}

function attentionKey(item) {
  return [item.kind, item.projectId, item.taskId || 0, item.reportId || 0, item.title].join('-')
}

function hrefFor(item) {
  const params = new URLSearchParams()
  if (item.kind === 'MISSING_REPORT') {
    params.set('section', 'reports')
    params.set('compose', '1')
  } else if (item.taskId) {
    params.set('section', 'tasks')
    params.set('task', String(item.taskId))
  } else {
    params.set('section', 'reports')
    if (item.reportId) params.set('report', String(item.reportId))
  }
  return `/projects/${item.projectId}?${params.toString()}`
}

function attentionLabel(kind) {
  if (kind === 'REVIEW') return 'Needs review'
  if (kind === 'REVISION') return 'Needs revision'
  if (kind === 'OVERDUE') return 'Overdue'
  if (kind === 'MISSING_REPORT') return 'Report due'
  return label(kind)
}
