import { useEffect, useState } from 'react'
import { Button } from '@openai/apps-sdk-ui/components/Button'
import { ReportApiError } from '../reports/reportApi'
import { fetchHistoricalReview, type HistoricalReview } from './qaHistoryApi'
import { QaHistoricalReview } from './QaReviewHistory'

export function QaHistoricalReviewPanel({ projectId, reportId, resolutionId, onBack, onSessionExpired }: {
  projectId: string; reportId: string; resolutionId: string; onBack: () => void; onSessionExpired: () => void
}) {
  const [review, setReview] = useState<HistoricalReview | null>(null)
  const [error, setError] = useState(false)
  const [retry, setRetry] = useState(0)
  useEffect(() => {
    const controller = new AbortController()
    void fetchHistoricalReview(projectId, reportId, resolutionId, controller.signal).then(result => {
      if (!controller.signal.aborted) setReview(result)
    }, failure => {
      if (controller.signal.aborted) return
      if (failure instanceof ReportApiError && failure.status === 401) onSessionExpired()
      else setError(true)
    })
    return () => controller.abort()
  }, [projectId, reportId, resolutionId, retry, onSessionExpired])

  if (review) return <QaHistoricalReview review={review} onBack={onBack} />
  return <div className="qa-page-controls">
    {error ? <><p role="alert">Could not load this review.</p>
      <Button color="secondary" variant="soft" onClick={() => { setError(false); setRetry(value => value + 1) }}>Retry</Button>
    </> : <p role="status">Loading review…</p>}
    <Button color="secondary" variant="ghost" onClick={onBack}>Back</Button>
  </div>
}
