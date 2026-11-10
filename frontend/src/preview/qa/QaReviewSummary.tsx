import type { ReactNode } from 'react'
import { Badge } from '@openai/apps-sdk-ui/components/Badge'
import { Bug, Code, FileDocument, Folder } from '@openai/apps-sdk-ui/components/Icon'
import { Disclosure } from '../../shared/Disclosure'

import type { QaBug } from './qaFixtures'

export function QaReviewSummary({ bug, comment, showChangeMap = false, resolution, description, environment }: {
  bug: QaBug
  comment?: ReactNode
  showChangeMap?: boolean
  resolution?: ReactNode
  description?: string
  environment?: string
}) {
  const fix = [...bug.entries].reverse().find(entry => entry.title === 'Proposed fix')
  const checks = bug.checks
  const completed = checks.filter(check => check.result !== 'Pending').length
  const totalChangedLines = bug.fileChanges?.reduce((total, file) => total + file.additions + file.deletions, 0) ?? 0

  return (
    <article className="qa-review-summary" data-change-map={showChangeMap} aria-label="Bug review summary">
      <div className="qa-summary-main">
        <header className="qa-summary-heading">
          <div><Badge color={bug.status === 'Ready for QA' ? 'warning' : bug.status === 'Closed' ? 'success' : 'secondary'} pill>{bug.status === 'Ready for QA' ? 'Awaiting verification' : bug.status}</Badge> <span className="muted">{bug.id}</span></div>
          <h1>{bug.title}</h1>
          <div className="qa-summary-meta">
            <span><Folder aria-hidden="true" /> {bug.project}</span>
            {bug.build && <span>Build {bug.build}</span>}
            {checks.length > 0 && <span>{completed}/{checks.length} checked</span>}
          </div>
        </header>

        <p className="qa-summary-description">{description ?? bug.actual}</p>
        <Disclosure className="qa-activity-row" id="qa-review-report" tabIndex={-1} label={<><FileDocument aria-hidden="true" /> Original report</>}>
          <dl>
            {environment && <><dt>Environment</dt><dd>{environment}</dd></>}
            <dt>Expected behavior</dt><dd>{bug.expected}</dd>
            <dt>Actual behavior</dt><dd>{bug.actual}</dd>
            <dt>Steps to reproduce</dt><dd>{bug.steps}</dd>
          </dl>
        </Disclosure>
        {bug.logs && (
          <Disclosure className="qa-activity-row" id="qa-report-logs" tabIndex={-1} label={<><FileDocument aria-hidden="true" /> Report logs</>}>
            <pre>{bug.logs}</pre>
          </Disclosure>
        )}

        {resolution}
        {fix && (
          <section className="qa-summary-fix" id="qa-review-fix" tabIndex={-1}>
            <h2><Code aria-hidden="true" /> Proposed fix</h2>
            <p>{fix.text}</p>
          </section>
        )}

        <section className="qa-summary-activity" aria-labelledby="qa-activity-heading">
          <h2 id="qa-activity-heading">Activity</h2>
          <Disclosure className="qa-activity-row" label={<><Bug aria-hidden="true" /> Bug reported <span className="muted">Reporter</span></>}>
            <p>{bug.actual}</p>
          </Disclosure>
          {bug.entries.map((entry, index) => (
            <Disclosure className="qa-activity-row" key={index} label={<>{entry.title} <span className="muted">{entry.role}</span></>}>
              <p>{entry.text}</p>
            </Disclosure>
          ))}
        </section>
        {comment}
      </div>
      {showChangeMap && <section className="qa-change-map" aria-labelledby="qa-change-map-title">
        <h2 id="qa-change-map-title">Change map</h2>
        {bug.fileChanges?.length ? bug.fileChanges.map(file => (
          <div className="qa-change-map-file" key={file.path}>
            <div className="qa-change-map-label">
              <span title={file.path}>{file.path}</span>
              <span aria-label={`${file.additions} additions, ${file.deletions} deletions`}>+{file.additions} −{file.deletions}</span>
            </div>
            <div className="qa-change-map-track" aria-hidden="true">
              <span style={{ width: `${totalChangedLines ? (file.additions + file.deletions) / totalChangedLines * 100 : 0}%` }} />
            </div>
          </div>
        )) : <p className="muted">No patch attached.</p>}
      </section>}
    </article>
  )
}
