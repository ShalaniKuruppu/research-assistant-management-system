import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError, api } from '../api.js'
import { useAuth } from '../auth.jsx'
import Shell from '../Shell.jsx'

export default function NewProjectPage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const [error, setError] = useState('')
  const [pending, setPending] = useState(false)
  const [supervisors, setSupervisors] = useState([])
  const [loadingSupervisors, setLoadingSupervisors] = useState(true)

  const assistant = user.role === 'RESEARCH_ASSISTANT'

  useEffect(() => {
    if (!assistant) {
      setLoadingSupervisors(false)
      return
    }
    let cancelled = false
    api('/api/users?role=SUPERVISOR')
      .then((data) => {
        if (!cancelled) setSupervisors(data)
      })
      .catch((caught) => {
        if (!cancelled) {
          setError(caught instanceof ApiError ? caught.message : 'Could not load supervisors')
        }
      })
      .finally(() => {
        if (!cancelled) setLoadingSupervisors(false)
      })
    return () => {
      cancelled = true
    }
  }, [assistant])

  if (!assistant) {
    return (
      <Shell>
        <h1>New project</h1>
        <p className="lede">Only a research assistant can create a project.</p>
      </Shell>
    )
  }

  async function onSubmit(event) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const startDate = String(form.get('startDate'))
    const endDate = String(form.get('endDate'))
    if (endDate < startDate) {
      setError('The end date must be on or after the start date')
      return
    }
    const supervisorId = Number(form.get('supervisorId'))
    if (!supervisorId) {
      setError('Choose a supervisor')
      return
    }
    setError('')
    setPending(true)
    try {
      const project = await api('/api/projects', {
        method: 'POST',
        body: {
          title: String(form.get('title') || '').trim(),
          description: String(form.get('description') || '').trim(),
          objectives: String(form.get('objectives') || '').trim(),
          startDate,
          endDate,
          status: String(form.get('status')),
          supervisorId,
        },
      })
      navigate(`/projects/${project.id}`, { replace: true })
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Could not create the project')
    } finally {
      setPending(false)
    }
  }

  return (
    <Shell>
      <p className="eyebrow">Projects</p>
      <h1>New project</h1>
      <form className="card form-card" onSubmit={onSubmit}>
        {error ? <p className="form-error" role="alert">{error}</p> : null}
        <label>
          Title
          <input name="title" required maxLength={200} />
        </label>
        <label>
          Description
          <textarea name="description" rows={3} />
        </label>
        <label>
          Objectives
          <textarea name="objectives" rows={3} />
        </label>
        <label>
          Supervisor
          <select name="supervisorId" required disabled={loadingSupervisors || supervisors.length === 0} defaultValue="">
            <option value="" disabled>
              {loadingSupervisors ? 'Loading supervisors…' : 'Select a supervisor'}
            </option>
            {supervisors.map((person) => (
              <option key={person.id} value={person.id}>
                {person.name}
              </option>
            ))}
          </select>
        </label>
        {!loadingSupervisors && supervisors.length === 0 ? (
          <p className="meta">No active supervisors are available yet.</p>
        ) : null}
        <div className="split">
          <label>
            Start date
            <input name="startDate" type="date" required />
          </label>
          <label>
            End date
            <input name="endDate" type="date" required />
          </label>
        </div>
        <label>
          Status
          <select name="status" defaultValue="PLANNED">
            <option value="PLANNED">Planned</option>
            <option value="ACTIVE">Active</option>
            <option value="ON_HOLD">On hold</option>
            <option value="COMPLETED">Completed</option>
            <option value="ARCHIVED">Archived</option>
          </select>
        </label>
        <button className="primary" type="submit" disabled={pending || loadingSupervisors || supervisors.length === 0}>
          {pending ? 'Creating…' : 'Create project'}
        </button>
      </form>
    </Shell>
  )
}
