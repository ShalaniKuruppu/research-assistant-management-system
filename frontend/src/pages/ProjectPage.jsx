import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError, api } from '../api.js'
import { useAuth } from '../auth.jsx'
import { label } from '../format.js'
import Shell from '../Shell.jsx'

export default function ProjectPage() {
  const { projectId } = useParams()
  const { user } = useAuth()
  const supervisor = user.role === 'SUPERVISOR'
  const [project, setProject] = useState(null)
  const [tasks, setTasks] = useState([])
  const [reports, setReports] = useState([])
  const [candidates, setCandidates] = useState([])
  const [section, setSection] = useState('assistants')
  const [selectedTask, setSelectedTask] = useState(null)
  const [updates, setUpdates] = useState([])
  const [selectedReport, setSelectedReport] = useState(null)
  const [feedback, setFeedback] = useState([])
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [loading, setLoading] = useState(true)
  const [pending, setPending] = useState(false)

  async function load() {
    const requests = [
      api(`/api/projects/${projectId}`),
      api(`/api/projects/${projectId}/tasks`),
      api(`/api/projects/${projectId}/reports`),
    ]
    if (supervisor) {
      requests.push(api('/api/users?role=RESEARCH_ASSISTANT'))
    }
    const [projectData, taskData, reportData, people] = await Promise.all(requests)
    setProject(projectData)
    setTasks(taskData)
    setReports(reportData)
    setCandidates(people || [])
  }

  useEffect(() => {
    setLoading(true)
    load()
      .catch((caught) => setError(caught instanceof ApiError ? caught.message : 'Could not load this project'))
      .finally(() => setLoading(false))
  }, [projectId])

  async function run(action, success) {
    setError('')
    setNotice('')
    setPending(true)
    try {
      await action()
      await load()
      setNotice(success)
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'The request failed')
    } finally {
      setPending(false)
    }
  }

  async function showProgress(task) {
    setSelectedTask(task)
    setError('')
    try {
      setUpdates(await api(`/api/tasks/${task.id}/updates`))
    } catch (caught) {
      setUpdates([])
      setError(caught instanceof ApiError ? caught.message : 'Could not load progress')
    }
  }

  async function showReport(report) {
    setSelectedReport(report)
    setError('')
    try {
      setFeedback(await api(`/api/reports/${report.id}/feedback`))
    } catch (caught) {
      setFeedback([])
      setError(caught instanceof ApiError ? caught.message : 'Could not load feedback')
    }
  }

  const names = new Map((project?.assistants || []).map((assistant) => [assistant.assistantId, assistant.name]))
  const activeAssistants = (project?.assistants || []).filter((assistant) => assistant.active)

  return (
    <Shell>
      <p className="eyebrow"><Link to="/">Projects</Link></p>
      {loading ? <p className="status-line">Loading project…</p> : null}
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      {notice ? <p className="form-note" role="status">{notice}</p> : null}
      {project ? (
        <>
          <div className="page-head">
            <div>
              <h1>{project.title}</h1>
              <p className="meta">
                {label(project.status)} · {project.startDate} to {project.endDate}
                {project.supervisorName ? ` · ${project.supervisorName}` : ''}
              </p>
            </div>
          </div>
          {project.description ? <p className="lede">{project.description}</p> : null}
          {project.objectives ? <p className="meta">Objectives: {project.objectives}</p> : null}

          <div className="tabs" role="tablist">
            {['assistants', 'tasks', 'reports'].map((name) => (
              <button
                key={name}
                type="button"
                role="tab"
                aria-selected={section === name}
                className={section === name ? 'tab selected' : 'tab'}
                onClick={() => setSection(name)}
              >
                {name === 'assistants' ? 'Assistants' : name === 'tasks' ? 'Tasks' : 'Weekly reports'}
              </button>
            ))}
          </div>

          {section === 'assistants' ? (
            <section className="stack">
              {supervisor ? (
                <form
                  className="card"
                  onSubmit={(event) => {
                    event.preventDefault()
                    const assistantId = Number(new FormData(event.currentTarget).get('assistantId'))
                    event.currentTarget.reset()
                    run(
                      () => api(`/api/projects/${project.id}/assistants`, {
                        method: 'POST',
                        body: { assistantId },
                      }),
                      'Research assistant assigned.',
                    )
                  }}
                >
                  <h2>Assign a research assistant</h2>
                  {candidates.length === 0 ? (
                    <p className="meta">No active research assistant accounts exist yet. Register one, then assign it here.</p>
                  ) : (
                    <label>
                      Research assistant
                      <select name="assistantId" required defaultValue="">
                        <option value="" disabled>Choose an assistant</option>
                        {candidates.map((person) => (
                          <option key={person.id} value={person.id}>{person.name} · {person.email}</option>
                        ))}
                      </select>
                    </label>
                  )}
                  <button className="primary" type="submit" disabled={pending || candidates.length === 0}>
                    Assign
                  </button>
                </form>
              ) : null}
              {activeAssistants.length === 0 && (project.assistants || []).length === 0 ? (
                <p className="lede">No one is assigned to this project yet.</p>
              ) : null}
              {(project.assistants || []).map((assistant) => (
                <article className="list-card static" key={assistant.id}>
                  <div>
                    <h2>{assistant.name}</h2>
                    <p className="meta">{assistant.email} · {assistant.active ? 'Active' : 'Removed'}</p>
                  </div>
                  {supervisor && assistant.active ? (
                    <button
                      type="button"
                      disabled={pending}
                      onClick={() => run(
                        () => api(`/api/projects/${project.id}/assistants/${assistant.assistantId}`, {
                          method: 'PATCH',
                          body: { active: false },
                        }),
                        'Assignment removed.',
                      )}
                    >
                      Remove
                    </button>
                  ) : null}
                </article>
              ))}
            </section>
          ) : null}

          {section === 'tasks' ? (
            <section className="split-layout">
              <div className="stack">
                {supervisor ? (
                  <form
                    className="card"
                    onSubmit={(event) => {
                      event.preventDefault()
                      const form = new FormData(event.currentTarget)
                      const startDate = String(form.get('startDate'))
                      const dueDate = String(form.get('dueDate'))
                      if (dueDate < startDate) {
                        setError('The due date must be on or after the start date')
                        return
                      }
                      event.currentTarget.reset()
                      run(
                        () => api(`/api/projects/${project.id}/tasks`, {
                          method: 'POST',
                          body: {
                            assignedTo: Number(form.get('assignedTo')),
                            title: String(form.get('title') || '').trim(),
                            description: String(form.get('description') || '').trim(),
                            priority: String(form.get('priority')),
                            startDate,
                            dueDate,
                            expectedDeliverables: String(form.get('expectedDeliverables') || '').trim(),
                          },
                        }),
                        'Task created.',
                      )
                    }}
                  >
                    <h2>Create a task</h2>
                    {activeAssistants.length === 0 ? (
                      <p className="meta">Assign an active research assistant before creating a task.</p>
                    ) : (
                      <label>
                        Assign to
                        <select name="assignedTo" required defaultValue="">
                          <option value="" disabled>Choose an assistant</option>
                          {activeAssistants.map((assistant) => (
                            <option key={assistant.assistantId} value={assistant.assistantId}>{assistant.name}</option>
                          ))}
                        </select>
                      </label>
                    )}
                    <label>
                      Title
                      <input name="title" required maxLength={200} />
                    </label>
                    <label>
                      Description
                      <textarea name="description" rows={2} />
                    </label>
                    <label>
                      Priority
                      <select name="priority" defaultValue="MEDIUM">
                        <option value="LOW">Low</option>
                        <option value="MEDIUM">Medium</option>
                        <option value="HIGH">High</option>
                      </select>
                    </label>
                    <div className="split">
                      <label>
                        Start
                        <input name="startDate" type="date" required />
                      </label>
                      <label>
                        Due
                        <input name="dueDate" type="date" required />
                      </label>
                    </div>
                    <label>
                      Expected deliverables
                      <textarea name="expectedDeliverables" rows={2} />
                    </label>
                    <button className="primary" type="submit" disabled={pending || activeAssistants.length === 0}>
                      Create task
                    </button>
                  </form>
                ) : null}
                {tasks.length === 0 ? <p className="lede">No tasks yet.</p> : null}
                {tasks.map((task) => (
                  <button
                    className={selectedTask?.id === task.id ? 'list-card static selected-card' : 'list-card static'}
                    key={task.id}
                    type="button"
                    onClick={() => showProgress(task)}
                  >
                    <div>
                      <h2>{task.title}</h2>
                      <p className="meta">
                        {names.get(task.assignedTo) || 'Assistant'} · {label(task.priority)} · due {task.dueDate}
                      </p>
                    </div>
                    <span className="role">{label(task.status)} · {task.completionPercentage}%</span>
                  </button>
                ))}
              </div>
              <aside className="card">
                <h2>Task progress</h2>
                {selectedTask ? (
                  <>
                    <p className="meta">{selectedTask.title}</p>
                    {updates.length === 0 ? <p>No progress notes yet.</p> : null}
                    <ol className="timeline">
                      {updates.map((update) => (
                        <li key={update.id}>
                          <strong>{label(update.status)} · {update.completionPercentage}%</strong>
                          <p>{update.progressNote}</p>
                          {update.blockerNote ? <p className="meta">Blocker: {update.blockerNote}</p> : null}
                        </li>
                      ))}
                    </ol>
                  </>
                ) : (
                  <p className="meta">Select a task to read its progress notes.</p>
                )}
              </aside>
            </section>
          ) : null}

          {section === 'reports' ? (
            <section className="split-layout">
              <div className="stack">
                {reports.length === 0 ? <p className="lede">No weekly reports yet.</p> : null}
                {reports.map((report) => (
                  <button
                    className={selectedReport?.id === report.id ? 'list-card static selected-card' : 'list-card static'}
                    key={report.id}
                    type="button"
                    onClick={() => showReport(report)}
                  >
                    <div>
                      <h2>{names.get(report.assistantId) || 'Assistant'}</h2>
                      <p className="meta">{report.weekStartDate} to {report.weekEndDate}</p>
                    </div>
                    <span className="role">
                      {label(report.status)}
                      {report.latestOutcome ? ` · ${label(report.latestOutcome)}` : ''}
                    </span>
                  </button>
                ))}
              </div>
              <aside className="card">
                <h2>Review</h2>
                {selectedReport ? (
                  <>
                    <p className="meta">{names.get(selectedReport.assistantId) || 'Assistant'} · {label(selectedReport.status)}</p>
                    <ReportBody report={selectedReport} />
                    <h3>Feedback</h3>
                    {feedback.length === 0 ? <p className="meta">No feedback yet.</p> : null}
                    <ol className="timeline">
                      {feedback.map((item) => (
                        <li key={item.id}>
                          <strong>{label(item.outcome)}</strong>
                          <p>{item.feedbackText}</p>
                        </li>
                      ))}
                    </ol>
                    {supervisor && selectedReport.status !== 'DRAFT' ? (
                      <form
                        className="stack"
                        onSubmit={(event) => {
                          event.preventDefault()
                          const form = new FormData(event.currentTarget)
                          const reportId = selectedReport.id
                          event.currentTarget.reset()
                          run(async () => {
                            await api(`/api/reports/${reportId}/feedback`, {
                              method: 'POST',
                              body: {
                                feedbackText: String(form.get('feedbackText') || '').trim(),
                                outcome: String(form.get('outcome')),
                              },
                            })
                            const freshReports = await api(`/api/projects/${project.id}/reports`)
                            const fresh = freshReports.find((report) => report.id === reportId)
                            if (fresh) {
                              setSelectedReport(fresh)
                              setFeedback(await api(`/api/reports/${reportId}/feedback`))
                            }
                          }, 'Feedback saved.')
                        }}
                      >
                        <label>
                          Feedback
                          <textarea name="feedbackText" rows={3} required />
                        </label>
                        <label>
                          Outcome
                          <select name="outcome" defaultValue="APPROVED">
                            <option value="APPROVED">Approved</option>
                            <option value="NEEDS_REVISION">Needs revision</option>
                          </select>
                        </label>
                        <button className="primary" type="submit" disabled={pending}>Save feedback</button>
                      </form>
                    ) : null}
                    {supervisor && selectedReport.status === 'DRAFT' ? (
                      <p className="meta">Feedback can be written after the assistant submits this report.</p>
                    ) : null}
                  </>
                ) : (
                  <p className="meta">Select a weekly report to review it.</p>
                )}
              </aside>
            </section>
          ) : null}
        </>
      ) : null}
    </Shell>
  )
}

function ReportBody({ report }) {
  const fields = [
    ['Work completed', report.workCompleted],
    ['Current tasks', report.currentTasks],
    ['Findings', report.findings],
    ['Problems and support', report.problemsSupport],
    ['Next week', report.nextWeekPlan],
    ['Links', report.supportingLinks],
  ]
  return (
    <div className="stack">
      {fields.map(([title, value]) => value ? (
        <div key={title}>
          <p className="meta">{title}</p>
          <p>{value}</p>
        </div>
      ) : null)}
    </div>
  )
}
