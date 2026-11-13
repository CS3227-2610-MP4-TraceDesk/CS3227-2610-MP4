import type { ReactNode } from 'react'
import { Code } from '@openai/apps-sdk-ui/components/Icon'
import { ReportDetailLayout } from '../../reports/ReportDetailLayout'
import type { QaBug } from './qaFixtures'

export function QaReviewSummary({ bug, comment, activityContent, showChangeMap = false, resolution, description, environment }: {
  bug: QaBug
  comment?: ReactNode
  activityContent?: ReactNode
  showChangeMap?: boolean
  resolution?: ReactNode
  description?: string
  environment?: string
}) {
  const fix = [...bug.entries].reverse().find(entry => entry.title === 'Proposed fix')
  const completed = bug.checks.filter(check => check.result !== 'Pending').length
  return <ReportDetailLayout report={{
    id: bug.id, title: bug.title,
    statusLabel: bug.status === 'Ready for QA' ? 'Awaiting verification' : bug.status,
    statusColor: bug.status === 'Ready for QA' ? 'warning' : bug.status === 'Closed' ? 'success' : 'secondary',
    projectName: bug.project, description: description ?? bug.actual, environment: environment ?? '',
    expectedBehavior: bug.expected, actualBehavior: bug.actual, reproductionSteps: bug.steps,
    textLogs: bug.logs ?? '', fileChanges: bug.fileChanges,
  }} metadata={<>{bug.build && <span>Build {bug.build}</span>}{bug.checks.length > 0 && <span>{completed}/{bug.checks.length} checked</span>}</>}
    showChangeMap={showChangeMap}
    panels={<>{resolution}{fix && <section className="qa-summary-fix" id="qa-review-fix" tabIndex={-1}><h2><Code aria-hidden="true" /> Proposed fix</h2><p>{fix.text}</p></section>}</>}
    activityContent={<>{bug.entries.map((entry, index) => <div className="qa-activity-row" key={index}><p>{entry.title} · {entry.role}</p><p>{entry.text}</p></div>)}{activityContent}</>}
    footer={comment} />
}
