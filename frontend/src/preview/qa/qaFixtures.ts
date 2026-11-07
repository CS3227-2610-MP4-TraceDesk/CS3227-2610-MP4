type FileChange = { path: string; additions: number; deletions: number }
type ReviewEntry = { role: string; title: string; text: string }

export type QaBug = {
  id: string
  title: string
  status: 'Ready for QA' | 'Closed' | 'Investigating' | 'Needs clarification'
  project: string
  lastActivity: string
  expected: string
  actual: string
  steps: string
  logs?: string
  build?: string
  reviewedByMe?: boolean
  checks?: readonly ('Pass' | 'Fail' | 'Pending')[]
  entries: readonly ReviewEntry[]
  fileChanges?: readonly FileChange[]
}

export const qaBugs: readonly QaBug[] = [
  {
    id: 'TD-023',
    title: 'Refund totals are duplicated',
    status: 'Ready for QA',
    project: 'Payments',
    lastActivity: '2026-11-06',
    expected: 'Each refund appears once.',
    actual: 'The same refund appears twice after a refresh.',
    steps: 'Open a refunded payment, then refresh.',
    build: '1.4.2',
    checks: ['Pass', 'Pass', 'Fail', 'Pending', 'Pending'],
    entries: [{
      role: 'Developer',
      title: 'Proposed fix',
      text: 'Deduplicate by refund ID. Confirmed totals locally on build 1.4.2.',
    }],
    fileChanges: [
      { path: 'refund-total.ts', additions: 18, deletions: 7 },
      { path: 'refund-total.test.ts', additions: 8, deletions: 0 },
    ],
  },
  {
    id: 'TD-024',
    title: 'CSV export fails without dates',
    status: 'Investigating',
    project: 'Payments',
    lastActivity: '2026-11-05',
    expected: 'Export all payments when no date filter is set.',
    actual: 'Export returns “Invalid date range”.',
    steps: 'Open Payments, clear the date filter, then export as CSV.',
    logs: 'GET /api/payments/export → 400 INVALID_DATE_RANGE',
    entries: [],
  },
  {
    id: 'TD-019',
    title: 'Search resets on page change',
    status: 'Needs clarification',
    project: 'Payments',
    lastActivity: '2026-11-04',
    expected: 'Keep the active query when paging.',
    actual: 'Page two clears the query.',
    steps: 'Search for a payment and go to page two.',
    entries: [{
      role: 'Developer',
      title: 'Clarification requested',
      text: 'Which search term and browser reproduced this?',
    }],
  },
]

export const qaQueues = [
  { label: 'Awaiting verification', matches: (bug: QaBug) => bug.status === 'Ready for QA' },
  { label: 'Reviewed by me', matches: (bug: QaBug) => bug.reviewedByMe === true },
  { label: 'All bugs', matches: () => true },
] as const

export function matchesBugSearch(bug: QaBug, search: string) {
  return `${bug.id} ${bug.title}`.toLowerCase().includes(search.trim().toLowerCase())
}
