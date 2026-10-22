import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError, api } from '../api.js'
import { useAuth } from '../auth.jsx'
import Shell from '../Shell.jsx'

export default function NewProjectPage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const [error, setError] = useState('')
  const [pending, setPending] = useState(false)

  if (user.role !== 'SUPERVISOR') {
    return (
      <Shell>
        <h1>New project</h1>
        <p className="lede">Only a supervisor can create a project.</p>
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
        <button className="primary" type="submit" disabled={pending}>
          {pending ? 'Creating…' : 'Create project'}
        </button>
      </form>
    </Shell>
  )
}
