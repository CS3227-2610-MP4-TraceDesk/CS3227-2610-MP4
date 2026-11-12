import { Button } from '@openai/apps-sdk-ui/components/Button'
import { Badge } from '@openai/apps-sdk-ui/components/Badge'
import type { VerificationCheck } from '../preview/qa/qaFixtures'
import { Disclosure } from '../shared/Disclosure'
import './qa-review-history.css'

export type QaHistoryEntry = {
  reportId: string
  title: string
  resolutionId: string
  revision: number
  decision: 'CLOSE' | 'SEND_BACK'
  reviewedAt: string
  reviewerUserId: string
}

type QaReviewHistoryProps = {
  entries: QaHistoryEntry[]
  onOpen: (entry: QaHistoryEntry) => void
}

function decisionLabel(decision: QaHistoryEntry['decision']) {
  return decision === 'CLOSE' ? 'Closed' : 'Sent back'
}

function formatReviewTime(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.valueOf()) ? value : date.toLocaleString()
}

export function QaReviewHistory({ entries, onOpen }: QaReviewHistoryProps) {
  return <section className="qa-review-history" aria-labelledby="qa-review-history-heading">
    <h2 id="qa-review-history-heading">Review history</h2>
    {entries.length === 0 ? <p className="qa-review-history-empty">No completed QA reviews yet.</p> :
      <ul className="qa-review-history-list">
        {entries.map(entry => <li key={entry.resolutionId}>
          <Button type="button" block color="secondary" variant="ghost" className="qa-review-history-item"
            aria-label={`${entry.title}, revision ${entry.revision}, ${decisionLabel(entry.decision)}`}
            onClick={() => onOpen(entry)}>
            <span className="qa-review-history-title">{entry.title}</span>
            <span className="qa-review-history-meta">
              <span>Revision {entry.revision}</span>
              <Badge color={entry.decision === 'CLOSE' ? 'success' : 'warning'}>{decisionLabel(entry.decision)}</Badge>
              <time dateTime={entry.reviewedAt}>{formatReviewTime(entry.reviewedAt)}</time>
            </span>
          </Button>
        </li>)}
      </ul>}
  </section>
}

export type QaHistoricalReviewEntry = QaHistoryEntry & {
  checks: VerificationCheck[]
  notes: string
  reason: string
}

type QaHistoricalReviewProps = {
  review: QaHistoricalReviewEntry
  onBack: () => void
}

export function QaHistoricalReview({ review, onBack }: QaHistoricalReviewProps) {
  return <article className="qa-historical-review" aria-labelledby="qa-historical-review-heading">
    <header className="qa-historical-review-header">
      <Button type="button" color="secondary" variant="ghost" onClick={onBack}>Back to review history</Button>
      <Badge color={review.decision === 'CLOSE' ? 'success' : 'warning'}>{decisionLabel(review.decision)}</Badge>
    </header>
    <h2 id="qa-historical-review-heading">{review.title}</h2>
    <p className="qa-historical-review-meta">
      Revision {review.revision} · <span>Reviewed by {review.reviewerUserId}</span> ·{' '}
      <time dateTime={review.reviewedAt}>{formatReviewTime(review.reviewedAt)}</time>
    </p>

    <section className="qa-historical-review-section" aria-labelledby="qa-historical-checks-heading">
      <h3 id="qa-historical-checks-heading">Verification checks</h3>
      {review.checks.length === 0 ? <p>No checks were recorded for this review.</p> :
        <ul className="qa-historical-check-list">
          {review.checks.map((check, index) => <li key={`${index}-${check.title}`}>
            <Disclosure className="qa-historical-check" label={<>
              <span className="qa-historical-check-title">{check.title}</span>
              <span className="qa-historical-check-result">{check.result}</span>
            </>}>
              <dl>
                <div><dt>Check</dt><dd>{check.title}</dd></div>
                <div><dt>Result</dt><dd>{check.result}</dd></div>
                <div><dt>Check notes</dt><dd>{check.notes ?? ''}</dd></div>
              </dl>
            </Disclosure>
          </li>)}
        </ul>}
    </section>

    <section className="qa-historical-review-section" aria-label="Review notes and reason">
      <dl className="qa-historical-review-fields">
        <div><dt>Verification notes</dt><dd>{review.notes}</dd></div>
        {review.reason && <div><dt>Reason for sending back</dt><dd>{review.reason}</dd></div>}
      </dl>
    </section>
  </article>
}
