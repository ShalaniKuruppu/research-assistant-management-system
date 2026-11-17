import { useEffect, useState } from 'react'
import { ApiError, api } from '../api.js'
import { useAuth } from '../auth.jsx'
import { formatWhen, label } from '../format.js'
import Shell from '../Shell.jsx'

const STATUSES = ['PLANNED', 'IN_PROGRESS', 'ON_HOLD', 'COMPLETED']

export default function DevelopmentProjectsPage() {
  const { user } = useAuth()
  const assistant = user.role === 'RESEARCH_ASSISTANT'
  const supervisor = user.role === 'SUPERVISOR'
  const [items, setItems] = useState([])
  const [selectedId, setSelectedId] = useState(null)
  const [composing, setComposing] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [loading, setLoading] = useState(true)
  const [pending, setPending] = useState(false)

  const selected = items.find((item) => item.id === selectedId) || null

  async function load() {
    return api('/api/development-projects')
  }

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    load()
      .then((rows) => {
        if (cancelled) return
        setItems(rows)
        if (rows[0]) setSelectedId(rows[0].id)
      })
      .catch((caught) => {
        if (!cancelled) {
          setError(caught instanceof ApiError ? caught.message : 'Could not load development projects')
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  async function run(action, success) {
    setError('')
    setNotice('')
    setPending(true)
    try {
      const result = await action()
      const rows = await load()
      setItems(rows)
      if (result?.id) {
        setSelectedId(result.id)
        setComposing(false)
      } else if (!rows.some((item) => item.id === selectedId)) {
        setSelectedId(rows[0]?.id || null)
      }
      setNotice(success)
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'The request failed')
    } finally {
      setPending(false)
    }
  }

  return (
    <Shell>
      <div className="page-head">
        <div>
          <p className="eyebrow">Development</p>
          <h1>Development projects</h1>
          <p className="lede">
            {assistant
              ? 'Track your own development work as a separate activity from research projects.'
              : 'Browse development activities filed by research assistants.'}
          </p>
        </div>
        {assistant ? (
          <button
            className="button primary"
            type="button"
            onClick={() => {
              setComposing(true)
              setSelectedId(null)
              setError('')
              setNotice('')
            }}
          >
            New development project
          </button>
        ) : null}
      </div>
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      {notice ? <p className="form-note" role="status">{notice}</p> : null}
      {loading ? <p className="status-line">Loading development projects…</p> : null}
      {!loading ? (
        <section className="split-layout">
          <div className="stack">
            {!items.length && !composing ? (
              <p className="lede">
                {assistant
                  ? 'No development projects yet. Create one to track implementation work.'
                  : 'No development projects have been filed yet.'}
              </p>
            ) : null}
            {items.map((item) => (
              <button
                key={item.id}
                type="button"
                className={
                  !composing && selectedId === item.id
                    ? 'list-card static selected-card'
                    : 'list-card static'
                }
                aria-pressed={!composing && selectedId === item.id}
                onClick={() => {
                  setComposing(false)
                  setSelectedId(item.id)
                  setError('')
                  setNotice('')
                }}
              >
                <div>
                  <h2>{item.title}</h2>
                  <p className="meta">
                    {supervisor && item.assistantName ? `${item.assistantName} · ` : ''}
                    {item.updatedAt ? `Updated ${formatWhen(item.updatedAt)}` : 'Development activity'}
                  </p>
                </div>
                <span className="role">{label(item.status)}</span>
              </button>
            ))}
          </div>
          <aside className="card">
            {composing && assistant ? (
              <DevelopmentForm
                key="new"
                pending={pending}
                submitLabel="Create development project"
                onSubmit={(body) => run(
                  () => api('/api/development-projects', { method: 'POST', body }),
                  'Development project created.',
                )}
              />
            ) : selected ? (
              assistant ? (
                <DevelopmentForm
                  key={`${selected.id}-${selected.updatedAt}`}
                  item={selected}
                  pending={pending}
                  submitLabel="Save updates"
                  onSubmit={(body) => run(
                    () => api(`/api/development-projects/${selected.id}`, { method: 'PATCH', body }),
                    'Development project updated.',
                  )}
                />
              ) : (
                <DevelopmentDetails item={selected} />
              )
            ) : (
              <p className="meta">
                {assistant
                  ? 'Select a development project, or create a new one.'
                  : 'Select a development project to review it.'}
              </p>
            )}
          </aside>
        </section>
      ) : null}
    </Shell>
  )
}

function DevelopmentForm({ item, pending, submitLabel, onSubmit }) {
  return (
    <form
      className="stack"
      onSubmit={(event) => {
        event.preventDefault()
        const form = new FormData(event.currentTarget)
        onSubmit({
          title: String(form.get('title') || '').trim(),
          description: String(form.get('description') || '').trim(),
          objectives: String(form.get('objectives') || '').trim(),
          repositoryUrl: String(form.get('repositoryUrl') || '').trim(),
          status: String(form.get('status')),
          progressNote: String(form.get('progressNote') || '').trim(),
        })
      }}
    >
      <h2>{item ? item.title : 'New development project'}</h2>
      <p className="meta">
        {item
          ? 'Update status, progress, and links for this development work.'
          : 'This is independent of research projects and visible to supervisors.'}
      </p>
      <label>
        Title
        <input name="title" required maxLength={200} defaultValue={item?.title || ''} />
      </label>
      <label>
        Description
        <textarea name="description" rows={3} maxLength={20000} defaultValue={item?.description || ''} />
      </label>
      <label>
        Objectives
        <textarea name="objectives" rows={3} maxLength={20000} defaultValue={item?.objectives || ''} />
      </label>
      <label>
        Repository / demo URL
        <input
          name="repositoryUrl"
          type="url"
          maxLength={1000}
          placeholder="https://github.com/..."
          defaultValue={item?.repositoryUrl || ''}
        />
      </label>
      <label>
        Status
        <select name="status" defaultValue={item?.status || 'PLANNED'}>
          {STATUSES.map((status) => (
            <option key={status} value={status}>{label(status)}</option>
          ))}
        </select>
      </label>
      <label>
        Progress note
        <textarea name="progressNote" rows={4} maxLength={20000} defaultValue={item?.progressNote || ''} />
      </label>
      <button className="primary" type="submit" disabled={pending}>
        {pending ? 'Saving…' : submitLabel}
      </button>
    </form>
  )
}

function DevelopmentDetails({ item }) {
  return (
    <div className="stack">
      <h2>{item.title}</h2>
      <p className="meta">
        {label(item.status)}
        {item.assistantName ? ` · ${item.assistantName}` : ''}
        {item.updatedAt ? ` · Updated ${formatWhen(item.updatedAt)}` : ''}
      </p>
      {item.description ? (
        <div>
          <p className="meta">Description</p>
          <p>{item.description}</p>
        </div>
      ) : null}
      {item.objectives ? (
        <div>
          <p className="meta">Objectives</p>
          <p>{item.objectives}</p>
        </div>
      ) : null}
      {item.repositoryUrl ? (
        <p>
          <a href={item.repositoryUrl} target="_blank" rel="noreferrer">Open repository / demo</a>
        </p>
      ) : (
        <p className="meta">No repository link yet.</p>
      )}
      {item.progressNote ? (
        <div>
          <p className="meta">Progress note</p>
          <p>{item.progressNote}</p>
        </div>
      ) : (
        <p className="meta">No progress note yet.</p>
      )}
    </div>
  )
}
