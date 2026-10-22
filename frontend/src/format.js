const LABELS = {
  PLANNED: 'Planned',
  ACTIVE: 'Active',
  ON_HOLD: 'On hold',
  COMPLETED: 'Completed',
  ARCHIVED: 'Archived',
  TODO: 'To do',
  IN_PROGRESS: 'In progress',
  BLOCKED: 'Blocked',
  DONE: 'Done',
  CANCELLED: 'Cancelled',
  LOW: 'Low',
  MEDIUM: 'Medium',
  HIGH: 'High',
  DRAFT: 'Draft',
  SUBMITTED: 'Submitted',
  REVIEWED: 'Reviewed',
  APPROVED: 'Approved',
  NEEDS_REVISION: 'Needs revision',
}

export function label(value) {
  if (!value) return ''
  return LABELS[value] || value
}

export function formatWhen(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date)
}

export function currentWeek() {
  const today = new Date()
  const weekday = today.getDay()
  const monday = new Date(today)
  monday.setDate(today.getDate() - (weekday === 0 ? 6 : weekday - 1))
  const sunday = new Date(monday)
  sunday.setDate(monday.getDate() + 6)
  return {
    weekStartDate: isoDate(monday),
    weekEndDate: isoDate(sunday),
  }
}

function isoDate(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}
