import type { ReactNode } from 'react'
import { Badge } from '@openai/apps-sdk-ui/components/Badge'
import { Bug, FileDocument, Folder } from '@openai/apps-sdk-ui/components/Icon'
import { Disclosure } from '../shared/Disclosure'
import './report-detail-layout.css'

export type ReportDetailLayoutData = {
  id: string
  title: string
  statusLabel: string
  statusColor: 'info' | 'warning' | 'success' | 'secondary'
  projectName: string
  description: string
  environment: string
  expectedBehavior: string
  actualBehavior: string
  reproductionSteps: string
  textLogs: string
  fileChanges?: readonly { path: string; additions: number; deletions: number }[]
}

export function ReportDetailLayout({ report, topActions, metadata, panels, activityContent, activitySection, footer, showChangeMap = false }: {
  report: ReportDetailLayoutData
  topActions?: ReactNode
  metadata?: ReactNode
  panels?: ReactNode
  activityContent?: ReactNode
  activitySection?: ReactNode
  footer?: ReactNode
  showChangeMap?: boolean
}) {
  const totalChangedLines = report.fileChanges?.reduce((total, file) => total + file.additions + file.deletions, 0) ?? 0
  return <article className="qa-review-summary report-detail-layout" data-change-map={showChangeMap} aria-label="Bug report detail">
    <div className="qa-summary-main">
      <header className="qa-summary-heading">
        <div><Badge color={report.statusColor} pill>{report.statusLabel}</Badge><span className="muted">{report.id}</span>{topActions && <div className="report-detail-actions">{topActions}</div>}</div>
        <h1>{report.title}</h1>
        <div className="qa-summary-meta"><span><Folder aria-hidden="true" /> {report.projectName}</span>{metadata}</div>
      </header>
      <p className="qa-summary-description">{report.description}</p>
      <Disclosure className="qa-activity-row" id="qa-review-report" tabIndex={-1} label={<><FileDocument aria-hidden="true" /> Original report</>}>
        <dl className="qa-summary-fields">
          {report.environment && <div><dt>Environment</dt><dd>{report.environment}</dd></div>}
          <div><dt>Expected behavior</dt><dd>{report.expectedBehavior}</dd></div>
          <div><dt>Actual behavior</dt><dd>{report.actualBehavior}</dd></div>
          <div><dt>Steps to reproduce</dt><dd>{report.reproductionSteps}</dd></div>
        </dl>
      </Disclosure>
      {report.textLogs && <Disclosure className="qa-activity-row" id="qa-report-logs" tabIndex={-1} label={<><FileDocument aria-hidden="true" /> Report logs</>}><pre>{report.textLogs}</pre></Disclosure>}
      {panels}
      {activitySection ?? <section className="qa-summary-activity" aria-labelledby={`report-activity-${report.id}`}>
        <h2 id={`report-activity-${report.id}`}>Activity</h2>
        <Disclosure className="qa-activity-row qa-activity-event" label={<><Bug aria-hidden="true" /> Bug reported <span className="muted">Reporter</span></>}>
          <p>{report.actualBehavior}</p>
        </Disclosure>
        {activityContent}
      </section>}
      {footer}
    </div>
    {showChangeMap && <section className="qa-change-map" aria-labelledby="qa-change-map-title"><h2 id="qa-change-map-title">Change map</h2>{report.fileChanges?.length ? report.fileChanges.map(file => <div className="qa-change-map-file" key={file.path}><div className="qa-change-map-label"><span title={file.path}>{file.path}</span><span aria-label={`${file.additions} additions, ${file.deletions} deletions`}>+{file.additions} −{file.deletions}</span></div><div className="qa-change-map-track" aria-hidden="true"><span style={{ width: `${totalChangedLines ? (file.additions + file.deletions) / totalChangedLines * 100 : 0}%` }} /></div></div>) : <p className="muted">No patch attached.</p>}</section>}
  </article>
}
