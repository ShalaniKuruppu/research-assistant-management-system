import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, api } from '../api.js'
import { label } from '../format.js'
import Shell from '../Shell.jsx'

export default function DevelopmentProjectsPage() {
  const [projects, setProjects] = useState([])
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    api('/api/projects')
      .then((rows) => setProjects(rows.filter((project) => project.status === 'ACTIVE')))
      .catch((caught) => setError(caught instanceof ApiError ? caught.message : 'Could not load development projects'))
      .finally(() => setLoading(false))
  }, [])

  return (
    <Shell>
      <div className="page-head">
        <div>
          <p className="eyebrow">Development</p>
          <h1>Development projects</h1>
          <p className="lede">Active projects currently in progress.</p>
        </div>
      </div>
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      {loading ? <p className="status-line">Loading development projects…</p> : null}
      {!loading && projects.length === 0 ? (
        <p className="lede">No active development projects yet.</p>
      ) : null}
      <div className="stack">
        {projects.map((project) => (
          <Link className="list-card" key={project.id} to={`/projects/${project.id}`}>
            <div>
              <h2>{project.title}</h2>
              <p className="meta">
                {project.startDate} to {project.endDate}
                {project.supervisorName ? ` · ${project.supervisorName}` : ''}
              </p>
            </div>
            <span className="role">{label(project.status)}</span>
          </Link>
        ))}
      </div>
    </Shell>
  )
}
