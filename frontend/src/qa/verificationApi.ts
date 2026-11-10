import { AuthApiError, getCsrfToken } from '../auth/sessionApi'
import type { VerificationCheck } from '../preview/qa/qaFixtures'
import { ReportApiError } from '../reports/reportApi'

export type Decision = 'SAVE' | 'CLOSE' | 'SEND_BACK'
export type Verification = {
  version: number
  revision: number
  status: 'READY_FOR_QA' | 'CLOSED' | 'IN_PROGRESS'
  checks: VerificationCheck[]
  notes: string
  reason: string
  decision: Decision
}

export async function requestVerification(projectId: string, reportId: string, signal: AbortSignal, update?: {
  expectedVersion: number
  checks: VerificationCheck[]
  notes: string
  decision: Decision
  reason: string
}): Promise<Verification> {
  try {
    const token = update ? await getCsrfToken(signal) : undefined
    const response = await fetch(`/api/projects/${projectId}/reports/${reportId}/qa-verification`, {
      credentials: 'same-origin', cache: 'no-store', signal,
      ...(update && {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': token! },
        body: JSON.stringify({
          ...update,
          checks: update.checks.map(({ title, result, notes }) => ({ title, result, notes: notes ?? '' })),
        }),
      }),
    })
    const value = await response.json().catch(() => null)
    if (!response.ok) throw new ReportApiError(response.status, value?.error?.code)
    if (!value || !Number.isSafeInteger(value.version) || value.version < 1 ||
        !Number.isSafeInteger(value.revision) || value.revision < 1 ||
        !['READY_FOR_QA', 'CLOSED', 'IN_PROGRESS'].includes(value.status) ||
        !['SAVE', 'CLOSE', 'SEND_BACK'].includes(value.decision) ||
        typeof value.notes !== 'string' || typeof value.reason !== 'string' ||
        !Array.isArray(value.checks) || value.checks.length > 30 ||
        !value.checks.every((check: VerificationCheck) => check && typeof check.title === 'string' &&
          ['Pending', 'Pass', 'Fail'].includes(check.result) && typeof check.notes === 'string')) {
      throw new ReportApiError(502, 'INVALID_RESPONSE')
    }
    return value
  } catch (error) {
    if (error instanceof ReportApiError) throw error
    if (error instanceof AuthApiError) throw new ReportApiError(error.status, error.code)
    throw new ReportApiError(0, 'NETWORK_ERROR')
  }
}
