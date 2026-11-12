import { useCallback, useEffect, useId, useRef, useState } from 'react'
import { Button } from '@openai/apps-sdk-ui/components/Button'
import { fetchReportActivity, ReportApiError, type ReportActivityCode, type ReportActivityItem } from './reportApi'

const labels: Record<ReportActivityCode, string> = {
  REPORT_CREATED: 'Report submitted',
  DRAFT_SUBMITTED: 'Draft submitted as a report',
  REPORT_EDITED: 'Report updated',
  REPORT_INVESTIGATION_STARTED: 'Investigation started',
  REPORT_RESOLUTION_PROPOSED: 'Resolution proposed for QA review',
  QA_VERIFICATION_SAVED: 'QA verification updated',
  QA_VERIFICATION_CLOSED: 'QA verified and closed the report',
  QA_VERIFICATION_SENT_BACK: 'QA sent the report back for investigation',
}
const statuses = { OPEN: 'Open', IN_PROGRESS: 'In progress', READY_FOR_QA: 'Ready for QA', CLOSED: 'Closed' }

/** Read-only confirmed metadata; each mounted instance belongs to one report/version. */
export function ReportActivity({ projectId, reportId, onProtectedFailure, onRefresh }: {
  projectId: string; reportId: string; onProtectedFailure: (error: unknown) => unknown; onRefresh?: () => void
}) {
  const headingId = useId()
  const [items, setItems] = useState<ReportActivityItem[]>([])
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [loaded, setLoaded] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [expired, setExpired] = useState(false)
  const request = useRef<AbortController | null>(null)
  const sequence = useRef(0)
  const retryCursor = useRef<string | null>(null)

  const load = useCallback(async (cursor: string | null, append: boolean) => {
    request.current?.abort()
    const controller = new AbortController()
    const current = ++sequence.current
    request.current = controller
    retryCursor.current = cursor
    setLoading(true)
    setError(null)
    setExpired(false)
    try {
      const page = await fetchReportActivity(projectId, reportId, cursor, controller.signal)
      if (controller.signal.aborted || current !== sequence.current) return
      setItems(previous => append ? [...previous, ...page.items.filter(item => !previous.some(prior => prior.id === item.id))] : page.items)
      setNextCursor(page.nextCursor)
      setLoaded(true)
    } catch (failure) {
      if (controller.signal.aborted || current !== sequence.current) return
      if (failure instanceof ReportApiError && [401, 403, 404].includes(failure.status)) {
        setItems([])
        setNextCursor(null)
        setLoaded(false)
        setError(failure.status === 401 ? 'Your session has expired. Sign in again to view activity.' : 'Activity is unavailable to your account.')
        onProtectedFailure(failure)
      } else {
        const invalidCursor = failure instanceof ReportApiError && failure.code === 'INVALID_CURSOR'
        setExpired(invalidCursor)
        setError(invalidCursor ? 'This activity page has expired. Refresh activity to continue.' : 'Activity could not be loaded. Try again.')
      }
    } finally {
      if (!controller.signal.aborted && current === sequence.current) setLoading(false)
    }
  }, [onProtectedFailure, projectId, reportId])

  useEffect(() => {
    let active = true
    queueMicrotask(() => { if (active) void load(null, false) })
    return () => { active = false; request.current?.abort(); sequence.current += 1 }
  }, [load])

  return <section className="report-activity" aria-labelledby={headingId} aria-busy={loading}>
    <div className="report-section-heading">
      <div><h2 id={headingId}>Activity</h2><p className="muted">Confirmed report updates, oldest first.</p></div>
      <Button color="secondary" variant="outline" size="sm" disabled={loading} onClick={() => onRefresh ? onRefresh() : void load(null, false)}>Refresh activity</Button>
    </div>
    {items.length > 0 && <ol className="report-activity-list">{items.map(item => <li key={item.id}>
      <div><strong>{labels[item.eventCode]}</strong><time dateTime={item.occurredAt}>{new Date(item.occurredAt).toLocaleString()}</time></div>
      {item.newStatus && <p>{item.priorStatus ? `${statuses[item.priorStatus]} → ${statuses[item.newStatus]}` : statuses[item.newStatus]}</p>}
    </li>)}</ol>}
    {loading && <p className="report-empty-inline" role="status">Loading activity…</p>}
    {!loading && loaded && items.length === 0 && !error && <p className="report-empty-inline">No activity yet.</p>}
    {error && <div className="report-activity-error" role="alert"><p>{error}</p>{!expired && <Button color="secondary" variant="outline" size="sm" disabled={loading} onClick={() => void load(retryCursor.current, retryCursor.current !== null)}>Retry activity</Button>}</div>}
    {nextCursor && !error && <Button color="secondary" variant="outline" disabled={loading} onClick={() => void load(nextCursor, true)}>Load more activity</Button>}
  </section>
}
