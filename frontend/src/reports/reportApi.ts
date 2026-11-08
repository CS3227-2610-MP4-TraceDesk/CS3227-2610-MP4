import { AuthApiError, getCsrfToken } from '../auth/sessionApi'
import { reportFields, validateReport, type ReportValues } from './reportValidation'

export type ReportStatus = 'OPEN' | 'IN_PROGRESS' | 'READY_FOR_QA' | 'CLOSED'
export type ProjectContext = { project: { id: string; name: string }; role: 'REPORTER' }
export type ReportSummary = {
  id: string
  projectId: string
  reporterUserId: string
  title: string
  status: ReportStatus
  assignedDeveloperId: string | null
  createdAt: string
  updatedAt: string
  version: number
}
export type ReportDetail = ReportSummary & ReportValues
export type ReportPage = { items: ReportSummary[]; nextCursor: string | null }

export class ReportApiError extends Error {
  constructor(readonly status: number, readonly code?: string) {
    super('Report request failed')
    this.name = 'ReportApiError'
  }
}

const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/u
const statuses = new Set<ReportStatus>(['OPEN', 'IN_PROGRESS', 'READY_FOR_QA', 'CLOSED'])

export function isCanonicalUuid(value: string): boolean {
  return uuidPattern.test(value)
}

async function readCode(response: Response): Promise<string | undefined> {
  try {
    const body = await response.clone().json() as { error?: { code?: unknown } }
    return typeof body?.error?.code === 'string' ? body.error.code : undefined
  } catch {
    return undefined
  }
}

async function request(path: string, signal?: AbortSignal): Promise<Response> {
  try {
    return await fetch(path, { credentials: 'same-origin', cache: 'no-store', signal })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new ReportApiError(0, 'NETWORK_ERROR')
  }
}

async function jsonOrThrow<T>(response: Response, validate: (value: unknown) => value is T): Promise<T> {
  if (!response.ok) throw new ReportApiError(response.status, await readCode(response))
  let value: unknown
  try {
    value = await response.json()
  } catch {
    throw new ReportApiError(502, 'INVALID_RESPONSE')
  }
  if (!validate(value)) throw new ReportApiError(502, 'INVALID_RESPONSE')
  return value
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function isProjectContext(value: unknown, expectedId: string): value is ProjectContext {
  return isObject(value) && isObject(value.project) && value.project.id === expectedId &&
    typeof value.project.name === 'string' && value.project.name.length > 0 && value.role === 'REPORTER'
}

function isStatus(value: unknown): value is ReportStatus {
  return typeof value === 'string' && statuses.has(value as ReportStatus)
}

function isReportSummary(value: unknown, expectedProjectId: string): value is ReportSummary {
  return isObject(value) && isCanonicalUuid(String(value.id)) && value.projectId === expectedProjectId &&
    isCanonicalUuid(String(value.reporterUserId)) && typeof value.title === 'string' && isStatus(value.status) &&
    (value.assignedDeveloperId === null || (typeof value.assignedDeveloperId === 'string' && isCanonicalUuid(value.assignedDeveloperId))) &&
    typeof value.createdAt === 'string' && Number.isFinite(Date.parse(value.createdAt)) &&
    typeof value.updatedAt === 'string' && Number.isFinite(Date.parse(value.updatedAt)) &&
    Number.isInteger(value.version) && Number(value.version) > 0
}

function isReportDetail(value: unknown, expectedProjectId: string): value is ReportDetail {
  if (!isObject(value)) return false
  const candidate = value
  if (!isReportSummary(value, expectedProjectId)) return false
  if (!reportFields.every(field => typeof candidate[field] === 'string')) return false
  const reportValues = Object.fromEntries(reportFields.map(field => [field, candidate[field]])) as ReportValues
  return Object.keys(validateReport(reportValues)).length === 0
}

function isReportPage(value: unknown, projectId: string): value is ReportPage {
  return isObject(value) && Array.isArray(value.items) &&
    value.items.every(item => isReportSummary(item, projectId)) &&
    (value.nextCursor === null || typeof value.nextCursor === 'string')
}

export async function fetchProjectContext(projectId: string, signal?: AbortSignal): Promise<ProjectContext> {
  if (!isCanonicalUuid(projectId)) throw new ReportApiError(400, 'INVALID_REQUEST')
  const response = await request(`/api/projects/${projectId}/reporter-context`, signal)
  return jsonOrThrow(response, value => isProjectContext(value, projectId))
}

export async function fetchReports(projectId: string, status: ReportStatus | '', cursor: string | null, signal?: AbortSignal): Promise<ReportPage> {
  const params = new URLSearchParams({ limit: '25' })
  if (status) params.set('status', status)
  if (cursor) params.set('cursor', cursor)
  const response = await request(`/api/projects/${projectId}/reports?${params.toString()}`, signal)
  return jsonOrThrow(response, (value): value is ReportPage => isReportPage(value, projectId))
}

export async function fetchReport(projectId: string, reportId: string, signal?: AbortSignal): Promise<ReportDetail> {
  const response = await request(`/api/projects/${projectId}/reports/${reportId}`, signal)
  return jsonOrThrow(response, (value): value is ReportDetail => isReportDetail(value, projectId) && value.id === reportId)
}

export async function createReport(projectId: string, body: string, idempotencyKey: string, signal?: AbortSignal): Promise<ReportDetail> {
  let response: Response | null = null
  for (let attempt = 0; attempt < 2; attempt += 1) {
    let token: string
    try {
      token = await getCsrfToken(signal)
    } catch (error) {
      if (signal?.aborted) throw new DOMException('Request aborted', 'AbortError')
      if (error instanceof AuthApiError) throw new ReportApiError(error.status, error.code)
      throw new ReportApiError(0, 'NETWORK_ERROR')
    }
    if (signal?.aborted) throw new DOMException('Request aborted', 'AbortError')
    try {
      response = await fetch(`/api/projects/${projectId}/reports`, {
        method: 'POST',
        credentials: 'same-origin',
        cache: 'no-store',
        signal,
        headers: {
          'Content-Type': 'application/json',
          'X-CSRF-TOKEN': token,
          'Idempotency-Key': idempotencyKey,
        },
        body,
      })
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') throw error
      throw new ReportApiError(0, 'NETWORK_ERROR')
    }
    if (!(response.status === 403 && await readCode(response) === 'CSRF_INVALID' && attempt === 0)) break
  }
  if (response === null) throw new ReportApiError(0, 'NETWORK_ERROR')
  if (response.status !== 201) throw new ReportApiError(response.status, await readCode(response))
  let result: unknown
  try {
    result = await response.json()
  } catch {
    throw new ReportApiError(502, 'INVALID_RESPONSE')
  }
  if (!isReportDetail(result, projectId)) throw new ReportApiError(502, 'INVALID_RESPONSE')
  return result
}
