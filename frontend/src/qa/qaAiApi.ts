import { AuthApiError, getCsrfToken } from '../auth/sessionApi'
import { ReportApiError } from '../reports/reportApi'

export type QaSuggestion = { checks: string[]; evidenceGaps: string[] }

function isTextList(value: unknown): value is string[] {
  return Array.isArray(value) && value.length <= 5 && value.every(text =>
    typeof text === 'string' && text.trim() && Array.from(text).length <= 300)
}

export async function generateQaReview(projectId: string, reportId: string, version: number, signal: AbortSignal): Promise<QaSuggestion> {
  try {
    const token = await getCsrfToken(signal)
    if (signal.aborted) throw new DOMException('Request aborted', 'AbortError')
    const response = await fetch(`/api/projects/${projectId}/reports/${reportId}/qa-suggestions`, {
      method: 'POST', credentials: 'same-origin', cache: 'no-store', signal,
      headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': token },
      body: JSON.stringify({ expectedVersion: version }),
    })
    if (response.status !== 200) {
      const failure = await response.json().catch(() => null)
      throw new ReportApiError(response.status, typeof failure?.error?.code === 'string' ? failure.error.code : undefined)
    }
    const raw = await response.text()
    if (new TextEncoder().encode(raw).length > 20 * 1024) throw new ReportApiError(502, 'INVALID_RESPONSE')
    let body
    try { body = JSON.parse(raw) } catch { throw new ReportApiError(502, 'INVALID_RESPONSE') }
    if (body?.capability !== 'QA_PLANNING' || body.promptVersion !== 'qa-planning-v1' ||
        body.sourceId !== reportId || body.sourceVersion !== version ||
        !isTextList(body.checks) || !isTextList(body.evidenceGaps)) throw new ReportApiError(502, 'INVALID_RESPONSE')
    return { checks: body.checks, evidenceGaps: body.evidenceGaps }
  } catch (error) {
    if (error instanceof ReportApiError) throw error
    if (error instanceof AuthApiError) throw new ReportApiError(error.status, error.code)
    throw new ReportApiError(0, 'NETWORK_ERROR')
  }
}
