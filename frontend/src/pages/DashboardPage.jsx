import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, api } from '../api.js'
import { useAuth } from '../auth.jsx'
import { label } from '../format.js'
import Shell from '../Shell.jsx'

const REFRESH_MS = 30000

export default function DashboardPage() {
  const { user } = useAuth()
  const supervisor = user.role === 'SUPERVISOR'
  const [dashboard, setDashboard] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const requestId = useRef(0)

  useEffect(() => {
    let cancelled = false

    async function loadDashboard({ silent = false } = {}) {
      const id = ++requestId.current
      if (!silent) setLoading(true)
      try {
        const data = await api('/api/dashboard')
        if (cancelled || id !== requestId.current) return
        setDashboard(data)
        setError('')
      } catch (caught) {
        if (cancelled || id !== requestId.current) return
        setError(caught instanceof ApiError ? caught.message : 'Could not load the dashboard')
      } finally {
        if (!cancelled && id === requestId.current && !silent) setLoading(false)
      }
    }

    loadDashboard()

    function refreshIfVisible() {
      if (document.visibilityState === 'visible') {
        loadDashboard({ silent: true })
      }
    }

    function onFocus() {
      loadDashboard({ silent: true })
    }

    const intervalId = window.setInterval(() => {
      if (document.visibilityState === 'visible') {
        loadDashboard({ silent: true })
      }
    }, REFRESH_MS)

    document.addEventListener('visibilitychange', refreshIfVisible)
    window.addEventListener('focus', onFocus)
    return () => {
      cancelled = true
      window.clearInterval(intervalId)
      document.removeEventListener('visibilitychange', refreshIfVisible)
      window.removeEventListener('focus', onFocus)
    }
  }, [])

  const stats = dashboard ? [
    ['Total projects', dashboard.projectCount],
    ['Ongoing projects', dashboard.ongoingProjectCount],
    ['Blocked projects', dashboard.blockedProjectCount],
    ['Reimbursements', dashboard.reimbursementCount],
    ['Overdue projects', dashboard.overdueProjectCount],
    ['Pending projects', dashboard.pendingProjectCount],
    ['Completed projects', dashboard.completedProjectCount],
    ['No of research assistants', dashboard.researchAssistantCount],
  ] : []

  const projectAttention = dashboard
    ? dashboard.attention.filter((item) => !String(item.kind || '').startsWith('REIMBURSEMENT_'))
    : []
  const reimbursementAttention = dashboard
    ? dashboard.attention.filter((item) => String(item.kind || '').startsWith('REIMBURSEMENT_'))
    : []

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
        {!supervisor ? <Link className="button primary" to="/projects/new">New project</Link> : null}
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
          <section className="stack attention-section">
            <h2>Needs attention</h2>
            <div className="attention-blocks">
              <article className="attention-block">
                <h3>Projects</h3>
                {projectAttention.length === 0 ? (
                  <p className="lede">Nothing is waiting on you.</p>
                ) : null}
                <div className="stack">
                  {projectAttention.map((item) => (
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
              </article>
              <article className="attention-block">
                <h3>Reimbursements</h3>
                {reimbursementAttention.length === 0 ? (
                  <div className="attention-empty" role="status">
                    <span className="attention-empty-emoji" aria-hidden="true"></span>
                    <p className="lede">No reimbursements need attention.</p>
                  </div>
                ) : (
                  <div className="stack">
                    {reimbursementAttention.map((item) => (
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
                )}
              </article>
            </div>
          </section>
          <section className="stack">
            <div className="page-head">
              <h2>Projects</h2>
              <Link to="/projects">All projects</Link>
            </div>
            {dashboard.projects.length === 0 ? (
              <p className="lede">
                {supervisor
                  ? 'Waiting for a research assistant to create a project under you.'
                  : 'Create a project and choose your supervisor to get started.'}
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
          </section>
        </>
      ) : null}
    </Shell>
  )
}

function attentionKey(item) {
  return [
    item.kind,
    item.projectId,
    item.taskId || 0,
    item.reportId || 0,
    item.reimbursementId || 0,
    item.title,
  ].join('-')
}

function hrefFor(item) {
  const params = new URLSearchParams()
  if (item.kind === 'MISSING_REPORT') {
    params.set('section', 'reports')
    params.set('compose', '1')
  } else if (item.kind === 'REIMBURSEMENT_REVIEW' || item.kind === 'REIMBURSEMENT_REJECTED') {
    params.set('section', 'reimbursements')
    if (item.reimbursementId) params.set('reimbursement', String(item.reimbursementId))
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
  if (kind === 'REIMBURSEMENT_REVIEW') return 'Needs review'
  if (kind === 'REIMBURSEMENT_REJECTED') return 'Rejected'
  return label(kind)
}
