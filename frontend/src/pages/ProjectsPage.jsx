import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, api } from '../api.js'
import { useAuth } from '../auth.jsx'
import { label } from '../format.js'
import Shell from '../Shell.jsx'

export default function ProjectsPage() {
  const { user } = useAuth()
  const [projects, setProjects] = useState([])
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const supervisor = user.role === 'SUPERVISOR'

  useEffect(() => {
    api('/api/projects')
      .then(setProjects)
      .catch((caught) => setError(caught instanceof ApiError ? caught.message : 'Could not load projects'))
      .finally(() => setLoading(false))
  }, [])

  return (
    <Shell>
      <div className="page-head">
        <div>
          <p className="eyebrow">Projects</p>
          <h1>{supervisor ? 'Your projects' : 'Assigned projects'}</h1>
        </div>
        {supervisor ? <Link className="button primary" to="/projects/new">New project</Link> : null}
      </div>
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      {loading ? <p className="status-line">Loading projects…</p> : null}
      {!loading && projects.length === 0 ? (
        <p className="lede">
          {supervisor
            ? 'Create a project, then assign research assistants and tasks.'
            : 'A supervisor has not assigned you to a project yet.'}
        </p>
      ) : null}
      {!loading && !supervisor && projects.length > 0 ? (
        <p className="lede">Open a project to update your tasks and file a weekly report.</p>
      ) : null}
      <div className="stack">
        {projects.map((project) => (
          <Link className="list-card" key={project.id} to={`/projects/${project.id}`}>
            <div>
              <h2>{project.title}</h2>
              <p className="meta">
                {project.startDate} to {project.endDate}
                {supervisor ? '' : ` · ${project.supervisorName}`}
              </p>
            </div>
            <span className="role">{label(project.status)}</span>
          </Link>
        ))}
      </div>
    </Shell>
  )
}
