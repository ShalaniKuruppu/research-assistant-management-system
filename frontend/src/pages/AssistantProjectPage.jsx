import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError, api } from '../api.js'
import { currentWeek, formatWhen, label } from '../format.js'
import Shell from '../Shell.jsx'

const TASK_STATUSES = ['TODO', 'IN_PROGRESS', 'BLOCKED', 'DONE', 'CANCELLED']

const REPORT_FIELDS = [
  ['workCompleted', 'Work completed', 4],
  ['currentTasks', 'Current tasks', 3],
  ['findings', 'Findings', 3],
  ['problemsSupport', 'Problems and support', 3],
  ['nextWeekPlan', 'Next week', 3],
  ['supportingLinks', 'Links', 2],
]

export default function AssistantProjectPage() {
  const { projectId } = useParams()
  const [project, setProject] = useState(null)
  const [tasks, setTasks] = useState([])
  const [reports, setReports] = useState([])
  const [section, setSection] = useState('tasks')
  const [selectedTask, setSelectedTask] = useState(null)
  const [updates, setUpdates] = useState([])
  const [selectedReport, setSelectedReport] = useState(null)
  const [feedback, setFeedback] = useState([])
  const [composing, setComposing] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [loading, setLoading] = useState(true)
  const [pending, setPending] = useState(false)

  async function load() {
    const [projectData, taskData, reportData] = await Promise.all([
      api(`/api/projects/${projectId}`),
      api(`/api/projects/${projectId}/tasks`),
      api(`/api/projects/${projectId}/reports`),
    ])
    return { project: projectData, tasks: taskData, reports: reportData }
  }

  function apply(data) {
    setProject(data.project)
    setTasks(data.tasks)
    setReports(data.reports)
    return data
  }

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError('')
    setNotice('')
    setSelectedTask(null)
    setSelectedReport(null)
    setComposing(false)
    setSection('tasks')
    load()
      .then((data) => {
        if (cancelled) return
        apply(data)
        setSelectedTask(data.tasks[0] || null)
        if (data.reports[0]) {
          setSelectedReport(data.reports[0])
        } else {
          setComposing(true)
        }
      })
      .catch((caught) => {
        if (!cancelled) {
          setError(caught instanceof ApiError ? caught.message : 'Could not load this project')
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [projectId])

  useEffect(() => {
    if (!selectedTask) {
      setUpdates([])
      return undefined
    }
    let cancelled = false
    api(`/api/tasks/${selectedTask.id}/updates`)
      .then((rows) => {
        if (!cancelled) setUpdates(rows)
      })
      .catch((caught) => {
        if (cancelled) return
        setUpdates([])
        setError(caught instanceof ApiError ? caught.message : 'Could not load progress')
      })
    return () => {
      cancelled = true
    }
  }, [selectedTask?.id, selectedTask?.updatedAt])

  useEffect(() => {
    if (!selectedReport) {
      setFeedback([])
      return undefined
    }
    let cancelled = false
    api(`/api/reports/${selectedReport.id}/feedback`)
      .then((rows) => {
        if (!cancelled) setFeedback(rows)
      })
      .catch((caught) => {
        if (cancelled) return
        setFeedback([])
        setError(caught instanceof ApiError ? caught.message : 'Could not load feedback')
      })
    return () => {
      cancelled = true
    }
  }, [selectedReport?.id, selectedReport?.updatedAt])

  async function run(action, success) {
    setError('')
    setNotice('')
    setPending(true)
    try {
      const result = await action()
      const data = apply(await load())
      if (result?.taskId) {
        setSelectedTask(data.tasks.find((task) => task.id === result.taskId) || null)
      }
      if (result?.reportId) {
        setSelectedReport(data.reports.find((report) => report.id === result.reportId) || null)
        setComposing(false)
      }
      setNotice(success)
      return true
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'The request failed')
      return false
    } finally {
      setPending(false)
    }
  }

  async function saveProgress(body) {
    if (!selectedTask) return false
    return run(
      async () => {
        const progress = await api(`/api/tasks/${selectedTask.id}/updates`, {
          method: 'POST',
          body,
        })
        return { taskId: progress.task.id }
      },
      'Task progress saved.',
    )
  }

  async function saveReport(fields, submitting) {
    const draft = !selectedReport || selectedReport.status === 'DRAFT'
    const problem = reportProblem(fields, {
      includeWeek: draft,
      submitting,
      reports,
      currentId: selectedReport?.id,
    })
    if (problem) {
      setError(problem)
      setNotice('')
      return
    }

    setError('')
    setNotice('')
    setPending(true)
    let createdId = null
    try {
      let reportId = selectedReport?.id
      const body = reportBody(fields, draft)
      if (!reportId) {
        const created = await api(`/api/projects/${projectId}/reports`, {
          method: 'POST',
          body,
        })
        createdId = created.id
        reportId = created.id
      } else {
        await api(`/api/reports/${reportId}`, { method: 'PATCH', body })
      }
      if (submitting) {
        await api(`/api/reports/${reportId}/submit`, { method: 'POST' })
      }
      const data = apply(await load())
      setSelectedReport(data.reports.find((report) => report.id === reportId) || null)
      setComposing(false)
      setNotice(submitting ? 'Weekly report submitted.' : 'Weekly report saved.')
    } catch (caught) {
      if (createdId) {
        const data = await load().then(apply).catch(() => null)
        const fresh = data?.reports.find((report) => report.id === createdId)
        if (fresh) {
          setSelectedReport(fresh)
          setComposing(false)
        }
      }
      setError(caught instanceof ApiError ? caught.message : 'Could not save the weekly report')
    } finally {
      setPending(false)
    }
  }

  function openReport(report) {
    setSelectedReport(report)
    setComposing(false)
    setError('')
    setNotice('')
  }

  return (
    <Shell>
      <p className="eyebrow"><Link to="/">Assigned projects</Link></p>
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
                {project.supervisorName ? ` · Supervisor ${project.supervisorName}` : ''}
              </p>
            </div>
          </div>
          {project.description ? <p className="lede">{project.description}</p> : null}
          {project.objectives ? <p className="meta">Objectives: {project.objectives}</p> : null}

          <div className="tabs" role="tablist">
            <button
              type="button"
              role="tab"
              aria-selected={section === 'tasks'}
              className={section === 'tasks' ? 'tab selected' : 'tab'}
              onClick={() => setSection('tasks')}
            >
              Assigned tasks
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={section === 'reports'}
              className={section === 'reports' ? 'tab selected' : 'tab'}
              onClick={() => setSection('reports')}
            >
              Weekly reports
            </button>
          </div>

          {section === 'tasks' ? (
            <section className="split-layout">
              <div className="stack">
                {tasks.length === 0 ? (
                  <p className="lede">No tasks have been assigned to you on this project.</p>
                ) : null}
                {tasks.map((task) => (
                  <button
                    className={selectedTask?.id === task.id ? 'list-card static selected-card' : 'list-card static'}
                    key={task.id}
                    type="button"
                    aria-pressed={selectedTask?.id === task.id}
                    onClick={() => {
                      setSelectedTask(task)
                      setError('')
                      setNotice('')
                    }}
                  >
                    <div>
                      <h2>{task.title}</h2>
                      <p className="meta">
                        {label(task.priority)} · {task.startDate} to {task.dueDate}
                        {task.active ? '' : ' · Inactive'}
                      </p>
                      <div className="meter" aria-hidden="true">
                        <span style={{ width: `${task.completionPercentage}%` }} />
                      </div>
                    </div>
                    <span className="role">{label(task.status)} · {task.completionPercentage}%</span>
                  </button>
                ))}
              </div>
              <aside className="card">
                {selectedTask ? (
                  <>
                    <h2>{selectedTask.title}</h2>
                    <p className="meta">
                      {label(selectedTask.priority)} priority · due {selectedTask.dueDate}
                    </p>
                    {selectedTask.description ? <p>{selectedTask.description}</p> : null}
                    {selectedTask.expectedDeliverables ? (
                      <p className="meta">Expected deliverables: {selectedTask.expectedDeliverables}</p>
                    ) : null}
                    <h3>Progress</h3>
                    {updates.length === 0 ? <p className="meta">No progress notes yet.</p> : null}
                    <ol className="timeline">
                      {updates.map((update) => (
                        <li key={update.id}>
                          <strong>{label(update.status)} · {update.completionPercentage}%</strong>
                          <p className="meta">{formatWhen(update.createdAt)}</p>
                          <p>{update.progressNote}</p>
                          {update.blockerNote ? <p className="meta">Blocker: {update.blockerNote}</p> : null}
                        </li>
                      ))}
                    </ol>
                    <ProgressForm task={selectedTask} pending={pending} onSave={saveProgress} />
                  </>
                ) : (
                  <p className="meta">Select a task to read it and update its progress.</p>
                )}
              </aside>
            </section>
          ) : null}

          {section === 'reports' ? (
            <section className="split-layout">
              <div className="stack">
                <button
                  type="button"
                  className="primary"
                  onClick={() => {
                    setComposing(true)
                    setSelectedReport(null)
                    setError('')
                    setNotice('')
                  }}
                >
                  New weekly report
                </button>
                {reports.length === 0 ? <p className="lede">You have not filed a weekly report for this project.</p> : null}
                {reports.map((report) => (
                  <button
                    className={!composing && selectedReport?.id === report.id ? 'list-card static selected-card' : 'list-card static'}
                    key={report.id}
                    type="button"
                    aria-pressed={!composing && selectedReport?.id === report.id}
                    onClick={() => openReport(report)}
                  >
                    <div>
                      <h2>{report.weekStartDate} to {report.weekEndDate}</h2>
                      <p className="meta">
                        {report.submittedAt ? `Submitted ${formatWhen(report.submittedAt)}` : 'Not submitted'}
                      </p>
                    </div>
                    <span className="role">
                      {label(report.status)}
                      {report.latestOutcome ? ` · ${label(report.latestOutcome)}` : ''}
                    </span>
                  </button>
                ))}
              </div>
              <aside className="card">
                {composing || selectedReport ? (
                  <ReportPanel
                    report={composing ? null : selectedReport}
                    feedback={feedback}
                    supervisorName={project.supervisorName}
                    pending={pending}
                    onSave={saveReport}
                  />
                ) : (
                  <p className="meta">Select a weekly report, or start a new one.</p>
                )}
              </aside>
            </section>
          ) : null}
        </>
      ) : null}
    </Shell>
  )
}

function ProgressForm({ task, pending, onSave }) {
  const [status, setStatus] = useState(task.status)
  const [completion, setCompletion] = useState(String(task.completionPercentage))
  const [progressNote, setProgressNote] = useState('')
  const [blockerNote, setBlockerNote] = useState('')
  const [formError, setFormError] = useState('')

  useEffect(() => {
    setStatus(task.status)
    setCompletion(String(task.completionPercentage))
    setProgressNote('')
    setBlockerNote('')
    setFormError('')
  }, [task.id, task.status, task.completionPercentage, task.updatedAt])

  async function onSubmit(event) {
    event.preventDefault()
    const percentage = Number(completion)
    if (!Number.isInteger(percentage) || percentage < 0 || percentage > 100) {
      setFormError('Completion must be a whole number from 0 to 100')
      return
    }
    if (!progressNote.trim()) {
      setFormError('Progress note is required')
      return
    }
    if (status === 'BLOCKED' && !blockerNote.trim()) {
      setFormError('A blocked update needs a blocker note')
      return
    }
    setFormError('')
    await onSave({
      status,
      completionPercentage: percentage,
      progressNote: progressNote.trim(),
      blockerNote: blockerNote.trim(),
    })
  }

  return (
    <form className="stack" onSubmit={onSubmit}>
      <h3>Update progress</h3>
      {formError ? <p className="form-error" role="alert">{formError}</p> : null}
      <label>
        Status
        <select value={status} onChange={(event) => setStatus(event.target.value)}>
          {TASK_STATUSES.map((value) => (
            <option key={value} value={value}>{label(value)}</option>
          ))}
        </select>
      </label>
      <label>
        Completion percentage
        <input
          type="number"
          min="0"
          max="100"
          step="1"
          required
          value={completion}
          onChange={(event) => setCompletion(event.target.value)}
        />
      </label>
      <label>
        Progress note
        <textarea
          rows={3}
          required
          value={progressNote}
          onChange={(event) => setProgressNote(event.target.value)}
        />
      </label>
      <label>
        Blocker note
        <textarea
          rows={2}
          required={status === 'BLOCKED'}
          value={blockerNote}
          onChange={(event) => setBlockerNote(event.target.value)}
        />
      </label>
      {status === 'BLOCKED' ? <p className="meta">A blocked update needs a blocker note.</p> : null}
      <button className="primary" type="submit" disabled={pending}>
        {pending ? 'Saving…' : 'Save progress'}
      </button>
    </form>
  )
}

function ReportPanel({ report, feedback, supervisorName, pending, onSave }) {
  const draft = !report || report.status === 'DRAFT'
  const revising = report?.status === 'REVIEWED' && report.latestOutcome === 'NEEDS_REVISION'
  const editable = draft || revising
  const week = report
    ? { weekStartDate: report.weekStartDate, weekEndDate: report.weekEndDate }
    : currentWeek()

  return (
    <>
      <h2>{report ? 'Weekly report' : 'New weekly report'}</h2>
      {report ? (
        <p className="meta">
          {report.weekStartDate} to {report.weekEndDate} · {label(report.status)}
          {report.latestOutcome ? ` · ${label(report.latestOutcome)}` : ''}
        </p>
      ) : (
        <p className="meta">Save a draft, then submit it when the week is ready to review.</p>
      )}
      {report?.status === 'SUBMITTED' ? (
        <p className="form-note">Submitted. Waiting for supervisor feedback.</p>
      ) : null}
      {revising ? (
        <p className="form-note">Your supervisor asked for a revision. Update the report and save it.</p>
      ) : null}
      {report?.latestOutcome === 'APPROVED' ? (
        <p className="form-note">{supervisorName || 'Your supervisor'} approved this report.</p>
      ) : null}

      {editable ? (
        <form
          className="stack"
          key={report ? `${report.id}-${report.updatedAt}` : 'new'}
          onSubmit={(event) => {
            event.preventDefault()
            const form = new FormData(event.currentTarget)
            const submitting = event.nativeEvent.submitter?.value === 'submit'
            onSave(readReport(form), submitting)
          }}
        >
          {draft ? (
            <div className="split">
              <label>
                Week start
                <input name="weekStartDate" type="date" required defaultValue={week.weekStartDate} />
              </label>
              <label>
                Week end
                <input name="weekEndDate" type="date" required defaultValue={week.weekEndDate} />
              </label>
            </div>
          ) : null}
          {REPORT_FIELDS.map(([name, title, rows]) => (
            <label key={name}>
              {title}
              <textarea
                name={name}
                rows={rows}
                defaultValue={report?.[name] || ''}
              />
            </label>
          ))}
          <div className="actions">
            <button className="primary" type="submit" value="draft" disabled={pending}>
              {revising ? 'Save changes' : 'Save draft'}
            </button>
            {draft ? (
              <button type="submit" value="submit" disabled={pending}>Submit report</button>
            ) : null}
          </div>
        </form>
      ) : (
        <ReportBody report={report} />
      )}

      {report ? (
        <>
          <h3>Supervisor feedback</h3>
          {feedback.length === 0 ? <p className="meta">No supervisor feedback yet.</p> : null}
          <ol className="timeline">
            {feedback.map((item) => (
              <li key={item.id}>
                <strong>{label(item.outcome)}</strong>
                <p className="meta">{supervisorName || 'Supervisor'} · {formatWhen(item.createdAt)}</p>
                <p>{item.feedbackText}</p>
              </li>
            ))}
          </ol>
        </>
      ) : null}
    </>
  )
}

function ReportBody({ report }) {
  return (
    <div className="stack">
      {REPORT_FIELDS.map(([name, title]) => report[name] ? (
        <div key={name}>
          <p className="meta">{title}</p>
          <p>{report[name]}</p>
        </div>
      ) : null)}
    </div>
  )
}

function readReport(form) {
  return {
    weekStartDate: String(form.get('weekStartDate') || ''),
    weekEndDate: String(form.get('weekEndDate') || ''),
    workCompleted: String(form.get('workCompleted') || '').trim(),
    currentTasks: String(form.get('currentTasks') || '').trim(),
    findings: String(form.get('findings') || '').trim(),
    problemsSupport: String(form.get('problemsSupport') || '').trim(),
    nextWeekPlan: String(form.get('nextWeekPlan') || '').trim(),
    supportingLinks: String(form.get('supportingLinks') || '').trim(),
  }
}

function reportBody(fields, includeWeek) {
  const body = {
    workCompleted: fields.workCompleted,
    currentTasks: fields.currentTasks,
    findings: fields.findings,
    problemsSupport: fields.problemsSupport,
    nextWeekPlan: fields.nextWeekPlan,
    supportingLinks: fields.supportingLinks,
  }
  if (includeWeek) {
    body.weekStartDate = fields.weekStartDate
    body.weekEndDate = fields.weekEndDate
  }
  return body
}

function reportProblem(fields, { includeWeek, submitting, reports, currentId }) {
  if (includeWeek) {
    if (!fields.weekStartDate || !fields.weekEndDate) {
      return 'Choose the week start and end dates'
    }
    if (fields.weekEndDate < fields.weekStartDate) {
      return 'The week end must not come before the start'
    }
    const start = new Date(`${fields.weekStartDate}T00:00:00`)
    const end = new Date(`${fields.weekEndDate}T00:00:00`)
    const days = Math.round((end.getTime() - start.getTime()) / 86400000)
    if (days > 6) {
      return 'The report week must be 7 days or fewer'
    }
    const duplicate = reports.some((report) => (
      report.weekStartDate === fields.weekStartDate && report.id !== currentId
    ))
    if (duplicate) {
      return 'You already have a weekly report for that week'
    }
  }
  if (submitting && !fields.workCompleted) {
    return 'Work completed is required before a report can be submitted'
  }
  if (!includeWeek && !fields.workCompleted) {
    return 'Work completed is required once a report has been submitted'
  }
  return ''
}
