import { AuthApiError, getCsrfToken } from '../auth/sessionApi'
import { isCanonicalUuid, ReportApiError } from './reportApi'
import { reportFieldRules, reportFields, validateReport, type ReportValues } from './reportValidation'

export const aiFields = reportFields.filter((field): field is Exclude<typeof field, 'textLogs'> => field !== 'textLogs')
export type AiField = typeof aiFields[number]
export type AiSource = { kind: 'REPORT' | 'DRAFT'; id: string; version: number }
export type ReportSuggestion = {
  capability: 'REPORT_IMPROVEMENT'; promptVersion: 'report-improvement-v1'
  sourceKind: AiSource['kind']; sourceId: string; sourceVersion: number
  changes: { field: AiField; replacement: string }[]; missingInformation: string[]
}

function object(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}
function keys(value: Record<string, unknown>, expected: string[]): boolean {
  return Object.keys(value).length === expected.length && expected.every(key => Object.hasOwn(value, key))
}
function safeText(text: unknown, maxPoints: number, maxBytes: number): text is string {
  if (typeof text !== 'string' || Array.from(text).length > maxPoints || new TextEncoder().encode(text).length > maxBytes) return false
  const values = Object.fromEntries(reportFields.map(field => [field, field === 'textLogs' ? '' : 'present'])) as ReportValues
  values.description = text
  return !validateReport(values).description
}
export function isReportSuggestion(value: unknown, source: AiSource): value is ReportSuggestion {
  if (!object(value) || !keys(value, ['capability', 'promptVersion', 'sourceKind', 'sourceId', 'sourceVersion', 'changes', 'missingInformation']) ||
    value.capability !== 'REPORT_IMPROVEMENT' || value.promptVersion !== 'report-improvement-v1' ||
    value.sourceKind !== source.kind || value.sourceId !== source.id || value.sourceVersion !== source.version ||
    !Array.isArray(value.changes) || value.changes.length > 6 || !Array.isArray(value.missingInformation) || value.missingInformation.length > 5) return false
  const seen = new Set<string>()
  for (const change of value.changes) {
    if (!object(change) || !keys(change, ['field', 'replacement']) || typeof change.field !== 'string' ||
      !aiFields.includes(change.field as AiField) || seen.has(change.field)) return false
    seen.add(change.field)
    const rule = reportFieldRules[change.field as AiField]
    if (!safeText(change.replacement, rule.maxCodePoints, rule.maxBytes)) return false
  }
  return value.missingInformation.every(question => safeText(question, 500, 2000))
}

/** A generation is never replayed, including on CSRF or ambiguous network errors. */
export async function generateReportSuggestion(projectId: string, source: AiSource, signal?: AbortSignal): Promise<ReportSuggestion> {
  if (!isCanonicalUuid(projectId) || !isCanonicalUuid(source.id) || !Number.isSafeInteger(source.version) || source.version < 1 || !['REPORT', 'DRAFT'].includes(source.kind)) throw new ReportApiError(400, 'INVALID_REQUEST')
  try {
    const token = await getCsrfToken(signal)
    if (signal?.aborted) throw new DOMException('Request aborted', 'AbortError')
    const response = await fetch(`/api/projects/${projectId}/${source.kind === 'REPORT' ? 'reports' : 'drafts'}/${source.id}/suggestions`, {
      method: 'POST', credentials: 'same-origin', cache: 'no-store', signal,
      headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': token }, body: JSON.stringify({ expectedVersion: source.version }),
    })
    if (response.status !== 200) {
      let code: string | undefined
      try { const body: unknown = await response.json(); if (object(body) && object(body.error) && typeof body.error.code === 'string') code = body.error.code } catch { /* Fixed safe error below. */ }
      throw new ReportApiError(response.status, code)
    }
    let body: unknown
    try {
      const raw = await response.text()
      // Gateway output is at most 16 KiB; the trusted provenance envelope adds a small fixed amount.
      if (new TextEncoder().encode(raw).length > 20 * 1024) throw new Error('Invalid response')
      body = JSON.parse(raw)
    } catch { throw new ReportApiError(502, 'INVALID_RESPONSE') }
    if (!isReportSuggestion(body, source)) throw new ReportApiError(502, 'INVALID_RESPONSE')
    return body
  } catch (error) {
    if (signal?.aborted) throw new DOMException('Request aborted', 'AbortError')
    if (error instanceof ReportApiError) throw error
    if (error instanceof AuthApiError) throw new ReportApiError(error.status, error.code)
    throw new ReportApiError(0, 'NETWORK_ERROR')
  }
}
