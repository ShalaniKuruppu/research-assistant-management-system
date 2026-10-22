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
