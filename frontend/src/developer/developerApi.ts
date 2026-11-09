import { AuthApiError, getCsrfToken } from '../auth/sessionApi'
import { fetchReport, fetchReports, isCanonicalUuid, isReportDetail, type ReportDetail, type ReportPage, type ReportStatus } from '../reports/reportApi'

export type DeveloperContext = { project: { id: string; name: string }; role: 'DEVELOPER' }
export type InvestigationNote = {
  id: string
  reportId: string
  authorUserId: string
  lastEditedByUserId: string | null
  body: string
  createdAt: string
  updatedAt: string
  version: number
}
export type NotePage = { items: InvestigationNote[]; nextCursor: string | null }
export type ResolutionFields = {
  rootCause: string
  changeSummary: string
  verificationSteps: string
  fixReference: string
}
export type ProposedResolution = ResolutionFields & {
  id: string
  reportId: string
  authorUserId: string
  revision: number
  createdAt: string
}
export type ResolutionPage = { items: ProposedResolution[]; nextCursor: string | null }
export type ResolutionProposal = { report: ReportDetail; resolution: ProposedResolution }
export type DeveloperReportScope = 'assigned-to-me' | 'all'

export class DeveloperApiError extends Error {
  constructor(readonly status: number, readonly code?: string, readonly currentVersion?: number) {
    super('Developer request failed')
    this.name = 'DeveloperApiError'
  }
}

function object(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

async function failure(response: Response): Promise<DeveloperApiError> {
  try {
    const value: unknown = await response.clone().json()
    if (object(value)) {
      const error = object(value.error) ? value.error : undefined
      return new DeveloperApiError(
        response.status,
        typeof error?.code === 'string' ? error.code : undefined,
        Number.isInteger(value.currentVersion) ? Number(value.currentVersion) : undefined,
      )
    }
  } catch { /* A bounded generic failure is safer than exposing an invalid response. */ }
  return new DeveloperApiError(response.status)
}

async function read(path: string, signal?: AbortSignal): Promise<Response> {
  try {
    return await fetch(path, { credentials: 'same-origin', cache: 'no-store', signal })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new DeveloperApiError(0, 'NETWORK_ERROR')
  }
}

async function mutate(path: string, method: string, body?: string, idempotencyKey?: string, signal?: AbortSignal): Promise<Response> {
  for (let attempt = 0; attempt < 2; attempt += 1) {
    let token: string
    try { token = await getCsrfToken(signal) }
    catch (error) {
      if (signal?.aborted) throw new DOMException('Request aborted', 'AbortError')
      if (error instanceof AuthApiError) throw new DeveloperApiError(error.status, error.code)
      throw new DeveloperApiError(0, 'NETWORK_ERROR')
    }
    try {
      const response = await fetch(path, {
        method,
        credentials: 'same-origin',
        cache: 'no-store',
        signal,
        headers: {
          'X-CSRF-TOKEN': token,
          ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
          ...(idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {}),
        },
        ...(body === undefined ? {} : { body }),
      })
      const responseError = response.status === 403 ? await failure(response) : null
      if (responseError?.code === 'CSRF_INVALID' && attempt === 0) continue
      if (responseError) throw responseError
      return response
    } catch (error) {
      if (error instanceof DeveloperApiError) throw error
      if (error instanceof DOMException && error.name === 'AbortError') throw error
      throw new DeveloperApiError(0, 'NETWORK_ERROR')
    }
  }
  throw new DeveloperApiError(0, 'NETWORK_ERROR')
}

async function json<T>(response: Response, validate: (value: unknown) => value is T, expected = 200): Promise<T> {
  if (response.status !== expected) throw await failure(response)
  let value: unknown
  try { value = await response.json() } catch { throw new DeveloperApiError(502, 'INVALID_RESPONSE') }
  if (!validate(value)) throw new DeveloperApiError(502, 'INVALID_RESPONSE')
  return value
}

function isContext(value: unknown, projectId: string): value is DeveloperContext {
  return object(value) && object(value.project) && value.project.id === projectId &&
    typeof value.project.name === 'string' && value.project.name.length > 0 && value.role === 'DEVELOPER'
}

function isNote(value: unknown, projectId: string): value is InvestigationNote {
  return object(value) && isCanonicalUuid(String(value.id)) && isCanonicalUuid(String(value.reportId)) &&
    isCanonicalUuid(String(value.authorUserId)) &&
    (value.lastEditedByUserId === null || (typeof value.lastEditedByUserId === 'string' && isCanonicalUuid(value.lastEditedByUserId))) &&
    typeof value.body === 'string' && Array.from(value.body).length <= 8_000 &&
    typeof value.createdAt === 'string' && Number.isFinite(Date.parse(value.createdAt)) &&
    typeof value.updatedAt === 'string' && Number.isFinite(Date.parse(value.updatedAt)) &&
    Number.isInteger(value.version) && Number(value.version) > 0 && Boolean(projectId)
}

function isBoundedText(value: unknown, maximum: number): value is string {
  return typeof value === 'string' && Array.from(value).length <= maximum
}

function isResolution(value: unknown, reportId: string): value is ProposedResolution {
  return object(value) && isCanonicalUuid(String(value.id)) && value.reportId === reportId &&
    isCanonicalUuid(String(value.authorUserId)) && Number.isInteger(value.revision) && Number(value.revision) > 0 &&
    isBoundedText(value.rootCause, 4_000) && isBoundedText(value.changeSummary, 4_000) &&
    isBoundedText(value.verificationSteps, 4_000) && isBoundedText(value.fixReference, 200) &&
    typeof value.createdAt === 'string' && Number.isFinite(Date.parse(value.createdAt))
}

export async function fetchDeveloperContext(projectId: string, signal?: AbortSignal): Promise<DeveloperContext> {
  if (!isCanonicalUuid(projectId)) throw new DeveloperApiError(400, 'INVALID_REQUEST')
  return json(await read(`/api/projects/${projectId}/developer-context`, signal), value => isContext(value, projectId))
}

export function fetchDeveloperReports(projectId: string, scope: DeveloperReportScope, status: ReportStatus | '', cursor: string | null, signal?: AbortSignal): Promise<ReportPage> {
  return fetchReports(projectId, status, cursor, signal, scope === 'assigned-to-me' ? true : undefined)
}

export function fetchDeveloperReport(projectId: string, reportId: string, signal?: AbortSignal): Promise<ReportDetail> {
  return fetchReport(projectId, reportId, signal)
}

export async function startInvestigation(projectId: string, reportId: string, expectedVersion: number, signal?: AbortSignal): Promise<ReportDetail> {
  const response = await mutate(`/api/projects/${projectId}/reports/${reportId}/investigation/start`, 'POST', JSON.stringify({ expectedVersion }), undefined, signal)
  return json(response, (value): value is ReportDetail => isReportDetail(value, projectId) && value.id === reportId && value.status === 'IN_PROGRESS')
}

export async function fetchNotes(projectId: string, reportId: string, cursor: string | null, signal?: AbortSignal): Promise<NotePage> {
  const params = new URLSearchParams({ limit: '100' })
  if (cursor) params.set('cursor', cursor)
  return json(await read(`/api/projects/${projectId}/reports/${reportId}/notes?${params}`, signal), (value): value is NotePage =>
    object(value) && Array.isArray(value.items) && value.items.every(item => isNote(item, projectId) && item.reportId === reportId) &&
    (value.nextCursor === null || typeof value.nextCursor === 'string'))
}

export async function createNote(projectId: string, reportId: string, body: string, key: string, signal?: AbortSignal): Promise<InvestigationNote> {
  return json(await mutate(`/api/projects/${projectId}/reports/${reportId}/notes`, 'POST', JSON.stringify({ body }), key, signal),
    (value): value is InvestigationNote => isNote(value, projectId) && value.reportId === reportId, 201)
}

export async function updateNote(projectId: string, reportId: string, noteId: string, body: string, expectedVersion: number, signal?: AbortSignal): Promise<InvestigationNote> {
  return json(await mutate(`/api/projects/${projectId}/reports/${reportId}/notes/${noteId}`, 'PATCH', JSON.stringify({ body, expectedVersion }), undefined, signal),
    (value): value is InvestigationNote => isNote(value, projectId) && value.reportId === reportId && value.id === noteId)
}

export async function fetchResolutions(projectId: string, reportId: string, signal?: AbortSignal): Promise<ResolutionPage> {
  return json(await read(`/api/projects/${projectId}/reports/${reportId}/resolutions?limit=100`, signal), (value): value is ResolutionPage =>
    object(value) && Array.isArray(value.items) && value.items.every(item => isResolution(item, reportId)) &&
    (value.nextCursor === null || typeof value.nextCursor === 'string'))
}

export async function proposeResolution(projectId: string, reportId: string, fields: ResolutionFields, expectedVersion: number, signal?: AbortSignal): Promise<ResolutionProposal> {
  const response = await mutate(`/api/projects/${projectId}/reports/${reportId}/resolutions`, 'POST', JSON.stringify({ ...fields, expectedVersion }), undefined, signal)
  return json(response, (value): value is ResolutionProposal =>
    object(value) && isReportDetail(value.report, projectId) && value.report.id === reportId && value.report.status === 'READY_FOR_QA' &&
    isResolution(value.resolution, reportId), 201)
}

export async function deleteNote(projectId: string, reportId: string, noteId: string, expectedVersion: number, signal?: AbortSignal): Promise<void> {
  const response = await mutate(`/api/projects/${projectId}/reports/${reportId}/notes/${noteId}?expectedVersion=${expectedVersion}`, 'DELETE', undefined, undefined, signal)
  if (response.status !== 204) throw await failure(response)
}
