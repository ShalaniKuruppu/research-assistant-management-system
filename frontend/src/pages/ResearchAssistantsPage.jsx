import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, api } from '../api.js'
import { useAuth } from '../auth.jsx'
import { label } from '../format.js'
import Shell from '../Shell.jsx'

function ProfilePicture({ name, src }) {
  const [failed, setFailed] = useState(false)
  const initials = initialsFor(name)
  if (!src || failed) {
    return (
      <div className="assistant-avatar fallback" aria-hidden="true" title={name}>
        {initials}
      </div>
    )
  }
  return (
    <img
      className="assistant-avatar"
      src={src}
      alt=""
      width={72}
      height={72}
      onError={() => setFailed(true)}
    />
  )
}

function initialsFor(name) {
  const parts = String(name || '')
    .trim()
    .split(/\s+/)
    .filter(Boolean)
  if (parts.length === 0) return '?'
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase()
  return `${parts[0][0]}${parts[parts.length - 1][0]}`.toUpperCase()
}

async function loadAssistantDirectory() {
  try {
    return await api('/api/research-assistants')
  } catch (caught) {
    if (!(caught instanceof ApiError) || caught.status !== 404) {
      throw caught
    }
  }

  const [users, projects] = await Promise.all([
    api('/api/users?role=RESEARCH_ASSISTANT'),
    api('/api/projects'),
  ])

  return users.map((assistant) => ({
    id: assistant.id,
    name: assistant.name,
    email: assistant.email,
    phone: null,
    summary: null,
    profileImageUrl: null,
    active: assistant.active,
    projects: projects
      .filter((project) =>
        (project.assistants || []).some(
          (assignment) => assignment.assistantId === assistant.id && assignment.active
        )
      )
      .map((project) => ({
        id: project.id,
        title: project.title,
        status: project.status,
      })),
  }))
}

function sortAssistants(list) {
  return [...list].sort((left, right) => left.name.localeCompare(right.name))
}

export default function ResearchAssistantsPage() {
  const { user } = useAuth()
  const supervisor = user.role === 'SUPERVISOR'
  const [assistants, setAssistants] = useState([])
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [showForm, setShowForm] = useState(false)
  const [formError, setFormError] = useState('')
  const [formNotice, setFormNotice] = useState('')
  const [pending, setPending] = useState(false)
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [phone, setPhone] = useState('')

  useEffect(() => {
    if (!supervisor) {
      setLoading(false)
      return
    }
    let cancelled = false
    loadAssistantDirectory()
      .then((data) => {
        if (!cancelled) setAssistants(data)
      })
      .catch((caught) => {
        if (!cancelled) {
          setError(caught instanceof ApiError ? caught.message : 'Could not load research assistants')
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [supervisor])

  function resetForm() {
    setName('')
    setEmail('')
    setPassword('')
    setPhone('')
    setFormError('')
  }

  async function onCreate(event) {
    event.preventDefault()
    setFormError('')
    setFormNotice('')
    if (password.length < 8) {
      setFormError('Password must be at least 8 characters')
      return
    }
    setPending(true)
    try {
      const created = await api('/api/research-assistants', {
        method: 'POST',
        body: {
          name: name.trim(),
          email: email.trim(),
          password,
          phone: phone.trim() || null,
        },
      })
      setAssistants((current) => sortAssistants([...current, created]))
      resetForm()
      setShowForm(false)
      setFormNotice(`${created.name} was added and can sign in with the password you set.`)
      setError('')
    } catch (caught) {
      setFormError(caught instanceof ApiError ? caught.message : 'Could not add the research assistant')
    } finally {
      setPending(false)
    }
  }

  return (
    <Shell>
      <div className="page-head">
        <div>
          <p className="eyebrow">People</p>
          <h1>Research Assistants</h1>
          <p className="lede">
            {supervisor
              ? 'Summary, contact details, and allocated projects for each research assistant.'
              : 'Research assistant accounts are managed by supervisors.'}
          </p>
        </div>
        {supervisor ? (
          <button
            className="button primary"
            type="button"
            onClick={() => {
              setShowForm((open) => !open)
              setFormError('')
              setFormNotice('')
            }}
          >
            {showForm ? 'Cancel' : 'Add research assistant'}
          </button>
        ) : null}
      </div>
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      {formNotice ? <p className="form-note" role="status">{formNotice}</p> : null}
      {supervisor && showForm ? (
        <form className="card form-card" onSubmit={onCreate}>
          <h2>New research assistant</h2>
          <p className="meta">Creates a sign-in account they can use immediately.</p>
          {formError ? <p className="form-error" role="alert">{formError}</p> : null}
          <label>
            Name
            <input
              value={name}
              onChange={(event) => setName(event.target.value)}
              autoComplete="name"
              required
              maxLength={150}
            />
          </label>
          <label>
            Email
            <input
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              autoComplete="email"
              required
            />
          </label>
          <label>
            Temporary password
            <input
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              autoComplete="new-password"
              minLength={8}
              required
            />
          </label>
          <label>
            Phone <span className="meta">(optional)</span>
            <input
              type="tel"
              value={phone}
              onChange={(event) => setPhone(event.target.value)}
              autoComplete="tel"
              maxLength={40}
            />
          </label>
          <button className="primary" type="submit" disabled={pending}>
            {pending ? 'Adding…' : 'Add research assistant'}
          </button>
        </form>
      ) : null}
      {loading ? <p className="status-line">Loading research assistants…</p> : null}
      {!supervisor ? (
        <p className="lede">Only a supervisor can browse the research assistant directory.</p>
      ) : null}
      {!loading && !error && supervisor && assistants.length === 0 && !showForm ? (
        <p className="lede">No active research assistant accounts exist yet.</p>
      ) : null}
      <div className="stack">
        {assistants.map((assistant) => (
          <article className="assistant-card" key={assistant.id}>
            <div className="assistant-card-top">
              <ProfilePicture name={assistant.name} src={assistant.profileImageUrl} />
              <div className="assistant-card-identity">
                <div className="assistant-card-heading">
                  <h2>{assistant.name}</h2>
                  <span className="role">Research assistant</span>
                </div>
                <p className="assistant-summary">
                  {assistant.summary?.trim()
                    ? assistant.summary
                    : assistant.projects.length > 0
                      ? `Assigned to ${assistant.projects.length} of your ${assistant.projects.length === 1 ? 'project' : 'projects'}.`
                      : 'No summary yet. Not assigned to any of your projects.'}
                </p>
              </div>
            </div>

            <div className="assistant-card-grid">
              <section>
                <h3>Contact details</h3>
                <p className="meta">
                  <span className="assistant-label">Email</span>
                  <a href={`mailto:${assistant.email}`}>{assistant.email}</a>
                </p>
                <p className="meta">
                  <span className="assistant-label">Phone</span>
                  {assistant.phone?.trim() ? (
                    <a href={`tel:${assistant.phone}`}>{assistant.phone}</a>
                  ) : (
                    'Not provided'
                  )}
                </p>
              </section>

              <section>
                <h3>Allocated projects</h3>
                {assistant.projects.length === 0 ? (
                  <p className="meta">No projects allocated under you yet.</p>
                ) : (
                  <ul className="assistant-projects">
                    {assistant.projects.map((project) => (
                      <li key={project.id}>
                        <Link to={`/projects/${project.id}`}>{project.title}</Link>
                        <span className="role">{label(project.status)}</span>
                      </li>
                    ))}
                  </ul>
                )}
              </section>
            </div>
          </article>
        ))}
      </div>
    </Shell>
  )
}
