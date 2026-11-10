import { useEffect, useRef, useState } from 'react'
import { Button } from '@openai/apps-sdk-ui/components/Button'
import { Code, Sparkle } from '@openai/apps-sdk-ui/components/Icon'
import { DeveloperApiError, fetchResolutions, type ProposedResolution } from '../developer/developerApi'
import { QaReviewSummary } from '../preview/qa/QaReviewSummary'
import type { QaBug } from '../preview/qa/qaFixtures'
import { fetchReport, ReportApiError, type ReportDetail, type ReportStatus } from '../reports/reportApi'
import { QaVerification } from './QaVerification'
import { QaAiPanel } from './QaAiPanel'
import type { Verification } from './verificationApi'
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

export function QaReportSummary({ project, reportId, onSessionExpired, onDecision }: {
  project: { id: string; name: string }
  reportId: string
  onSessionExpired: () => void
  onDecision: (status: ReportStatus) => void
}) {
  const [detail, setDetail] = useState<{ report: ReportDetail; resolutions: ProposedResolution[] } | null>(null)
  const [error, setError] = useState<'failed' | 'denied' | null>(null)
  const [reload, setReload] = useState(0)
  const [verificationVisited, setVerificationVisited] = useState(false)
  const [verifying, setVerifying] = useState(false)
  const [review, setReview] = useState<Verification | null>(null)
  const [verificationBusy, setVerificationBusy] = useState(true)
  const [aiOpen, setAiOpen] = useState(false)
  const aiTrigger = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    const controller = new AbortController()
    void Promise.all([
      fetchReport(project.id, reportId, controller.signal),
      readResolutionHistory(project.id, reportId, controller.signal),
    ]).then(([report, resolutions]) => {
      if (!controller.signal.aborted) setDetail({ report, resolutions })
    }, failure => {
      if (controller.signal.aborted) return
      controller.abort()
      const status = failure instanceof ReportApiError || failure instanceof DeveloperApiError ? failure.status : 0
      if (status === 401) onSessionExpired()
      else setError([403, 404].includes(status) ? 'denied' : 'failed')
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

  function addSuggestedChecks(titles: string[]) {
    if (verificationBusy) return 'Wait for the Verification checklist to finish loading.'
    if (!review) return 'Open Verification and reload the checklist before adding checks.'
    if (review.version !== report.version || review.status !== 'READY_FOR_QA') {
      return 'The checklist version changed. Refresh the report before applying suggestions.'
    }
    const known = new Set(review.checks.map(check => check.title.trim().toLowerCase()))
    const additions = titles.flatMap(title => {
      const key = title.trim().toLowerCase()
      if (!key || known.has(key)) return []
      known.add(key)
      return [{ title: title.trim(), result: 'Pending' as const }]
    })
    if (!additions.length) return 'The selected checks are already in Verification.'
    if (review.checks.length + additions.length > 30) return 'Verification allows up to 30 checks. Select fewer suggestions.'
    setReview({ ...review, checks: [...review.checks, ...additions] })
    const skipped = titles.length - additions.length
    return `Added ${additions.length} pending ${additions.length === 1 ? 'check' : 'checks'} to the draft.${skipped ? ` Skipped ${skipped} already present.` : ''} Open Verification to save.`
  }

  return <section className="qa-authenticated-summary" aria-label="Bug review">
    <div className="qa-review-toolbar">
      <div className="qa-section-tabs" role="group" aria-label="Bug review sections">
        <Button color="secondary" variant="ghost" size="sm" selected={!verifying}
          aria-pressed={!verifying} onClick={() => setVerifying(false)}>Summary</Button>
        {resolutions.length > 0 && <Button color="secondary" variant="ghost" size="sm" selected={verifying}
          aria-pressed={verifying} onClick={() => {
            setVerificationVisited(true)
            setVerifying(true)
          }}>Verification</Button>}
      </div>
      {!verifying && report.status === 'READY_FOR_QA' && resolutions.length > 0 && (
        <Button ref={aiTrigger} className="qa-toolbar-action" color="secondary" variant="soft" size="sm"
          aria-expanded={aiOpen} onClick={() => {
            setVerificationVisited(true)
            setAiOpen(open => !open)
          }}>
          <Sparkle aria-hidden="true" />
          Review with AI
        </Button>
      )}
    </div>
    {verificationVisited && <div hidden={!verifying}>
      <QaVerification projectId={project.id} reportId={report.id} onSessionExpired={onSessionExpired}
        review={review} setReview={setReview} busy={verificationBusy} setBusy={setVerificationBusy}
        onSummary={() => setVerifying(false)} onSaved={review => {
          if (review.status !== 'READY_FOR_QA') onDecision(review.status)
          else setDetail({ ...detail, report: { ...report, version: review.version } })
        }} />
    </div>}
    <div hidden={verifying} className={aiOpen && !verifying ? 'qa-ai-layout' : undefined}>
    {aiOpen && <QaAiPanel key={report.version} projectId={project.id} reportId={report.id} version={report.version}
      onSessionExpired={onSessionExpired}
      onAddChecks={addSuggestedChecks}
      onClose={() => { setAiOpen(false); aiTrigger.current?.focus() }}
      onRefresh={() => {
        setAiOpen(false)
        setReview(null)
        setVerificationBusy(true)
        setVerificationVisited(false)
        setDetail(null)
        setReload(value => value + 1)
      }} />}
    <QaReviewSummary bug={{
      id: report.id, title: report.title, status: statuses[report.status], project: project.name,
      lastActivity: report.updatedAt, expected: report.expectedBehavior, actual: report.actualBehavior,
      steps: report.reproductionSteps, logs: report.textLogs, checks: [], entries: [],
    }} description={report.description} environment={report.environment}
      resolution={<section className="qa-summary-fix qa-resolution-history" aria-label="Developer resolutions">
        <h2><Code aria-hidden="true" /> Developer resolutions</h2>
        {!resolutions.length && <p className="muted">No proposed resolution was recorded.</p>}
        {resolutions.map(resolution => <Disclosure key={resolution.id} className="qa-activity-row"
          label={`Proposed fix · Revision ${resolution.revision}`}>
          <dl className="qa-summary-fields">
            <div><dt>Root cause</dt><dd>{resolution.rootCause}</dd></div>
            <div><dt>Change summary</dt><dd>{resolution.changeSummary}</dd></div>
            <div><dt>Verification steps</dt><dd>{resolution.verificationSteps}</dd></div>
            {resolution.fixReference && <div><dt>Fix reference</dt><dd>{resolution.fixReference}</dd></div>}
          </dl>
        </Disclosure>)}
      </section>}
    />
    </div>
  </section>
}
