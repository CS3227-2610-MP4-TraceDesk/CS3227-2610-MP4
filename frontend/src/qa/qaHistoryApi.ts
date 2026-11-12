import { ReportApiError } from '../reports/reportApi'
import type { VerificationCheck } from '../preview/qa/qaFixtures'
import type { QaHistoryEntry } from './QaReviewHistory'

export type HistoricalReview = QaHistoryEntry & { checks: VerificationCheck[]; notes: string; reason: string }

async function readHistory<T>(path: string, signal: AbortSignal): Promise<T> {
  const response = await fetch(path, { credentials: 'same-origin', cache: 'no-store', signal })
  const body = await response.json().catch(() => null)
  if (!response.ok) throw new ReportApiError(response.status, body?.error?.code)
  if (!body || typeof body !== 'object') throw new ReportApiError(502, 'INVALID_RESPONSE')
  return body as T
}

export function fetchReviewedByMe(projectId: string, before: string | null, signal: AbortSignal) {
  const path = '/api/projects/' + encodeURIComponent(projectId) + '/qa-reviewed'
  return readHistory<{ items: QaHistoryEntry[]; nextCursor: string | null }>(
    before ? path + '?' + new URLSearchParams({ before }) : path, signal,
  )
}

export function fetchHistoricalReview(projectId: string, reportId: string, resolutionId: string, signal: AbortSignal) {
  return readHistory<HistoricalReview>(
    '/api/projects/' + encodeURIComponent(projectId) + '/reports/' + encodeURIComponent(reportId) +
    '/qa-review-history/' + encodeURIComponent(resolutionId), signal,
  )
}
