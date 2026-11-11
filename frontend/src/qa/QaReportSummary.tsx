import { useEffect, useRef, useState } from 'react'
import { Button } from '@openai/apps-sdk-ui/components/Button'
import { Code } from '@openai/apps-sdk-ui/components/Icon'
import { DeveloperApiError, fetchResolutions, type ProposedResolution } from '../developer/developerApi'
import { QaReviewSummary } from '../preview/qa/QaReviewSummary'
import type { QaBug } from '../preview/qa/qaFixtures'
import { fetchReport, ReportApiError, type AssignmentResult, type ReportDetail, type ReportStatus } from '../reports/reportApi'
import { QaAiPanel } from './QaAiPanel'
import { QaAssignment } from './QaAssignment'
import { Disclosure } from '../shared/Disclosure'

const statuses: Record<ReportStatus, QaBug['status']> = {
  OPEN: 'Open',
  IN_PROGRESS: 'Investigating',
  READY_FOR_QA: 'Ready for QA',
  CLOSED: 'Closed',
}

async function readResolutionHistory(projectId: string, reportId: string, signal: AbortSignal) {
  const resolutions: ProposedResolution[] = []
  const seen = new Set<string>()
  let cursor: string | undefined

  do {
    const page = await fetchResolutions(projectId, reportId, signal, cursor)
    resolutions.push(...page.items)
    cursor = page.nextCursor ?? undefined
    if (cursor && seen.has(cursor)) throw new DeveloperApiError(502, 'INVALID_RESPONSE')
    if (cursor) seen.add(cursor)
  } while (cursor && !signal.aborted)

  return resolutions.sort((left, right) => right.revision - left.revision)
}

export function QaReportSummary({ project, reportId, onSessionExpired, onAssignmentConfirmed }: {
  project: { id: string; name: string }
  reportId: string
  onSessionExpired: () => void
  onAssignmentConfirmed: (reportId: string, result: AssignmentResult) => void
}) {
  const [detail, setDetail] = useState<{ report: ReportDetail; resolutions: ProposedResolution[] } | null>(null)
  const [error, setError] = useState<'failed' | 'denied' | null>(null)
  const [reload, setReload] = useState(0)
  const [aiOpen, setAiOpen] = useState(false)
  const aiTrigger = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    const controller = new AbortController()
    void Promise.all([
      fetchReport(project.id, reportId, controller.signal),
      readResolutionHistory(project.id, reportId, controller.signal),
    ]).then(([report, resolutions]) => {
      if (!controller.signal.aborted) {
        setDetail(current => current && current.report.version > report.version ? current : { report, resolutions })
        setError(null)
      }
    }, failure => {
      if (controller.signal.aborted) return
      controller.abort()
      const status = failure instanceof ReportApiError || failure instanceof DeveloperApiError ? failure.status : 0
      if (status === 401) onSessionExpired()
      else if ([403, 404].includes(status)) {
        setDetail(null)
        setError('denied')
      } else setError('failed')
    })
    return () => controller.abort()
  }, [project.id, reportId, reload, onSessionExpired])

  if (!detail) return <section className="qa-queue-view" aria-label="Bug review">
    {!error ? <p role="status">Loading bug summary…</p> : <>
      <p role="alert">{error === 'denied' ? 'This bug is no longer available to your account.' : 'Could not load the bug summary.'}</p>
      {error === 'failed' && <Button color="secondary" variant="outline" onClick={() => {
        setError(null)
        setReload(value => value + 1)
      }}>Retry</Button>}
    </>}
  </section>

  const { report, resolutions } = detail
  function refreshAssignment(confirmed?: AssignmentResult) {
    setAiOpen(false)
    setError(null)
    if (confirmed) {
      setDetail(current => current && { ...current, report: { ...current.report, ...confirmed } })
      onAssignmentConfirmed(reportId, confirmed)
    }
    setReload(value => value + 1)
  }

  return <section className="qa-authenticated-summary" aria-label="Bug review">
    <div className="qa-review-toolbar">
      <span className="qa-summary-label">Summary</span>
      {report.status === 'READY_FOR_QA' && resolutions.length > 0 && (
        <Button ref={aiTrigger} color="secondary" variant="soft" size="sm" aria-expanded={aiOpen}
          onClick={() => setAiOpen(open => !open)}>Review with AI</Button>
      )}
    </div>
    {error === 'failed' && <div className="qa-page-controls">
      <p role="alert">Could not refresh the bug summary.</p>
      <Button type="button" color="secondary" variant="outline" onClick={() => refreshAssignment()}>Retry summary refresh</Button>
    </div>}
    <div className={aiOpen ? 'qa-ai-layout' : undefined}>
    {aiOpen && <QaAiPanel projectId={project.id} reportId={report.id} version={report.version}
      onSessionExpired={onSessionExpired}
      onClose={() => { setAiOpen(false); aiTrigger.current?.focus() }}
      onRefresh={() => { setAiOpen(false); setDetail(null); setReload(value => value + 1) }} />}
    <QaReviewSummary bug={{
      id: report.id, title: report.title, status: statuses[report.status], project: project.name,
      lastActivity: report.updatedAt, expected: report.expectedBehavior, actual: report.actualBehavior,
      steps: report.reproductionSteps, logs: report.textLogs, checks: [], entries: [],
    }} description={report.description} environment={report.environment}
      resolution={<>
      <QaAssignment projectId={project.id} reportId={report.id} assignedDeveloperId={report.assignedDeveloperId}
        reportVersion={report.version} closed={report.status === 'CLOSED'} onSessionExpired={onSessionExpired} onRefresh={refreshAssignment} />
      <section className="qa-summary-fix qa-resolution-history" aria-label="Developer resolutions">
        <h2><Code aria-hidden="true" /> Developer resolutions</h2>
        {!resolutions.length && <p className="muted">No proposed resolution was recorded.</p>}
        {resolutions.map(resolution => <Disclosure key={resolution.id} className="qa-activity-row"
          label={`Proposed fix · Revision ${resolution.revision}`}>
          <dl>
            <dt>Root cause</dt><dd>{resolution.rootCause}</dd>
            <dt>Change summary</dt><dd>{resolution.changeSummary}</dd>
            <dt>Verification steps</dt><dd>{resolution.verificationSteps}</dd>
            {resolution.fixReference && <><dt>Fix reference</dt><dd>{resolution.fixReference}</dd></>}
          </dl>
        </Disclosure>)}
      </section></>}
    />
    </div>
  </section>
}
