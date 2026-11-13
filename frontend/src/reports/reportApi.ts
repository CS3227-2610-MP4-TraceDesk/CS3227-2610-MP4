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
export const reportActivityCodes = ['REPORT_CREATED', 'REPORT_EDITED', 'DRAFT_SUBMITTED', 'REPORT_INVESTIGATION_STARTED', 'REPORT_RESOLUTION_PROPOSED', 'QA_VERIFICATION_SAVED', 'QA_VERIFICATION_CLOSED', 'QA_VERIFICATION_SENT_BACK'] as const
export type ReportActivityCode = typeof reportActivityCodes[number]
export type ReportActivityItem = {
  id: string; eventCode: ReportActivityCode; occurredAt: string
  priorStatus: ReportStatus | null; newStatus: ReportStatus | null
  priorVersion: number | null; newVersion: number | null
}
export type ReportActivityPage = { items: ReportActivityItem[]; nextCursor: string | null }
export type DraftSummary = {
  id: string; projectId: string; title: string; createdAt: string; updatedAt: string
  expiresAt: string; version: number; submittedReportId: string | null
}
export type DraftDetail = DraftSummary & ReportValues
export type DraftPage = { items: DraftSummary[]; nextCursor: string | null }

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

function isActivityItem(value: unknown): value is ReportActivityItem {
  if (!isObject(value) || Object.keys(value).sort().join(',') !== 'eventCode,id,newStatus,newVersion,occurredAt,priorStatus,priorVersion') return false
  const version = (item: unknown) => item === null || (typeof item === 'number' && Number.isSafeInteger(item) && item > 0)
  return typeof value.id === 'string' && /^[1-9][0-9]{0,18}$/u.test(value.id) && BigInt(value.id) <= 9223372036854775807n &&
    reportActivityCodes.includes(value.eventCode as ReportActivityCode) &&
    typeof value.occurredAt === 'string' && Number.isFinite(Date.parse(value.occurredAt)) &&
    (value.priorStatus === null || isStatus(value.priorStatus)) && (value.newStatus === null || isStatus(value.newStatus)) &&
    version(value.priorVersion) && version(value.newVersion)
}

export async function fetchReportActivity(projectId: string, reportId: string, cursor: string | null, signal?: AbortSignal): Promise<ReportActivityPage> {
  const query = new URLSearchParams({ limit: '25' })
  if (cursor !== null) query.set('cursor', cursor)
  return jsonOrThrow(await request(`/api/projects/${projectId}/reports/${reportId}/activity?${query}`, signal),
    (value): value is ReportActivityPage => isObject(value) && Object.keys(value).sort().join(',') === 'items,nextCursor' &&
      Array.isArray(value.items) && value.items.length <= 100 && value.items.every(isActivityItem) &&
      (value.nextCursor === null || (typeof value.nextCursor === 'string' && value.nextCursor.length > 0 && value.nextCursor.length <= 4096)))
}

function isReportSummary(value: unknown, expectedProjectId: string): value is ReportSummary {
  return isObject(value) && isCanonicalUuid(String(value.id)) && value.projectId === expectedProjectId &&
    isCanonicalUuid(String(value.reporterUserId)) && typeof value.title === 'string' && isStatus(value.status) &&
    (value.assignedDeveloperId === null || (typeof value.assignedDeveloperId === 'string' && isCanonicalUuid(value.assignedDeveloperId))) &&
    typeof value.createdAt === 'string' && Number.isFinite(Date.parse(value.createdAt)) &&
    typeof value.updatedAt === 'string' && Number.isFinite(Date.parse(value.updatedAt)) &&
    Number.isInteger(value.version) && Number(value.version) > 0
}

export function isReportDetail(value: unknown, expectedProjectId: string): value is ReportDetail {
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

function isDraftSummary(value: unknown, projectId: string): value is DraftSummary {
  return isObject(value) && isCanonicalUuid(String(value.id)) && value.projectId === projectId &&
    typeof value.title === 'string' && ['createdAt', 'updatedAt', 'expiresAt'].every(key => typeof value[key] === 'string' && Number.isFinite(Date.parse(String(value[key])))) &&
    Number.isInteger(value.version) && Number(value.version) > 0 &&
    (value.submittedReportId === null || (typeof value.submittedReportId === 'string' && isCanonicalUuid(value.submittedReportId)))
}

function isDraftDetail(value: unknown, projectId: string): value is DraftDetail {
  if (!isObject(value) || !isDraftSummary(value, projectId)) return false
  const record = value as Record<string, unknown>
  if (!reportFields.every(field => typeof record[field] === 'string')) return false
  const fields = Object.fromEntries(reportFields.map(field => [field, record[field]])) as ReportValues
  return Object.keys(validateReport(Object.fromEntries(reportFields.map(field => [field, fields[field] || (field === 'textLogs' ? '' : 'draft ')])) as ReportValues)).length === 0
}

async function mutate(path: string, method: string, body: string | undefined, idempotencyKey?: string, signal?: AbortSignal): Promise<Response> {
  for (let attempt = 0; attempt < 2; attempt += 1) {
    let token: string
    try { token = await getCsrfToken(signal) }
    catch (error) {
      if (signal?.aborted) throw new DOMException('Request aborted', 'AbortError')
      if (error instanceof AuthApiError) throw new ReportApiError(error.status, error.code)
      throw new ReportApiError(0, 'NETWORK_ERROR')
    }
    if (signal?.aborted) throw new DOMException('Request aborted', 'AbortError')
    let response: Response
    try {
      response = await fetch(path, { method, credentials: 'same-origin', cache: 'no-store', signal,
        headers: { 'X-CSRF-TOKEN': token, ...(body === undefined ? {} : { 'Content-Type': 'application/json' }), ...(idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {}) }, body })
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') throw error
      throw new ReportApiError(0, 'NETWORK_ERROR')
    }
    if (!(response.status === 403 && await readCode(response) === 'CSRF_INVALID' && attempt === 0)) return response
  }
  throw new ReportApiError(0, 'NETWORK_ERROR')
}

async function mutationJson<T>(response: Response, validate: (value: unknown) => value is T, expectedStatus = 200): Promise<T> {
  if (response.status !== expectedStatus) throw new ReportApiError(response.status, await readCode(response))
  let value: unknown
  try { value = await response.json() } catch { throw new ReportApiError(502, 'INVALID_RESPONSE') }
  if (!validate(value)) throw new ReportApiError(502, 'INVALID_RESPONSE')
  return value
}

export async function fetchProjectContext(projectId: string, signal?: AbortSignal): Promise<ProjectContext> {
  if (!isCanonicalUuid(projectId)) throw new ReportApiError(400, 'INVALID_REQUEST')
  const response = await request(`/api/projects/${projectId}/reporter-context`, signal)
  return jsonOrThrow(response, value => isProjectContext(value, projectId))
}

export async function fetchReports(projectId: string, status: ReportStatus | '', cursor: string | null, signal?: AbortSignal, assignedToMe?: boolean): Promise<ReportPage> {
  const params = new URLSearchParams({ limit: '25' })
  if (status) params.set('status', status)
  if (cursor) params.set('cursor', cursor)
  if (assignedToMe !== undefined) params.set('assignedToMe', String(assignedToMe))
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

export async function fetchDrafts(projectId: string, signal?: AbortSignal, cursor: string | null = null): Promise<DraftPage> {
  const params = new URLSearchParams({ limit: '100' })
  if (cursor) params.set('cursor', cursor)
  const response = await request(`/api/projects/${projectId}/drafts?${params.toString()}`, signal)
  return jsonOrThrow(response, (value): value is DraftPage => isObject(value) && Array.isArray(value.items) && value.items.every(item => isDraftSummary(item, projectId)) && (value.nextCursor === null || typeof value.nextCursor === 'string'))
}

export async function fetchDraft(projectId: string, draftId: string, signal?: AbortSignal): Promise<DraftDetail> {
  const response = await request(`/api/projects/${projectId}/drafts/${draftId}`, signal)
  return jsonOrThrow(response, (value): value is DraftDetail => isDraftDetail(value, projectId) && value.id === draftId)
}

export async function createDraft(projectId: string, body: string, key: string, signal?: AbortSignal): Promise<DraftDetail> {
  return mutationJson(await mutate(`/api/projects/${projectId}/drafts`, 'POST', body, key, signal), (value): value is DraftDetail => isDraftDetail(value, projectId), 201)
}

export async function updateDraft(projectId: string, draftId: string, body: string, signal?: AbortSignal): Promise<DraftDetail> {
  return mutationJson(await mutate(`/api/projects/${projectId}/drafts/${draftId}`, 'PATCH', body, undefined, signal), (value): value is DraftDetail => isDraftDetail(value, projectId))
}

export async function deleteDraft(projectId: string, draftId: string, version: number, signal?: AbortSignal): Promise<void> {
  const response = await mutate(`/api/projects/${projectId}/drafts/${draftId}?expectedVersion=${version}`, 'DELETE', undefined, undefined, signal)
  if (response.status !== 204) throw new ReportApiError(response.status, await readCode(response))
}

export async function submitDraft(projectId: string, draftId: string, version: number, key: string, signal?: AbortSignal): Promise<ReportDetail & { submittedDraftId: string }> {
  return mutationJson(await mutate(`/api/projects/${projectId}/drafts/${draftId}/submit`, 'POST', JSON.stringify({ expectedVersion: version }), key, signal),
    (value): value is ReportDetail & { submittedDraftId: string } => {
      if (!isObject(value)) return false
      const record = value
      if (!isReportDetail(value, projectId)) return false
      return typeof record.submittedDraftId === 'string' && record.submittedDraftId === draftId
    }, 201)
}

export async function updateReport(projectId: string, reportId: string, body: string, signal?: AbortSignal): Promise<ReportDetail> {
  return mutationJson(await mutate(`/api/projects/${projectId}/reports/${reportId}`, 'PATCH', body, undefined, signal), (value): value is ReportDetail => isReportDetail(value, projectId) && value.id === reportId)
}

export type AssignmentResult = Pick<ReportSummary, 'assignedDeveloperId' | 'version' | 'updatedAt'>
export type AssignmentPage = Omit<AssignmentResult, 'updatedAt'> & {
  assignedDeveloperLogin: string | null
  assignmentEligible: boolean
  items: { id: string; login: string }[]
  nextCursor: string | null
}

function isAssignmentState(value: unknown): value is Record<string, unknown> & Omit<AssignmentResult, 'updatedAt'> {
  return isObject(value) && (value.assignedDeveloperId === null ||
    (typeof value.assignedDeveloperId === 'string' && isCanonicalUuid(value.assignedDeveloperId))) &&
    Number.isSafeInteger(value.version) && Number(value.version) > 0
}

export async function fetchAssignment(projectId: string, reportId: string, cursor: string | null, signal: AbortSignal): Promise<AssignmentPage> {
  const query = cursor ? '?' + new URLSearchParams({ cursor }).toString() : ''
  const response = await request(`/api/projects/${projectId}/reports/${reportId}/assignment${query}`, signal)
  return jsonOrThrow(response, (value): value is AssignmentPage => isAssignmentState(value) &&
    (value.assignedDeveloperId === null ? value.assignedDeveloperLogin === null :
      (typeof value.assignedDeveloperLogin === 'string' && value.assignedDeveloperLogin.length > 0 && value.assignedDeveloperLogin.length <= 254)) &&
    typeof value.assignmentEligible === 'boolean' && Array.isArray(value.items) &&
    value.items.every(item => isObject(item) && typeof item.id === 'string' && isCanonicalUuid(item.id) &&
      typeof item.login === 'string' && item.login.length > 0 && item.login.length <= 254) &&
    (value.nextCursor === null || (typeof value.nextCursor === 'string' && value.nextCursor.length > 0)))
}

export async function saveAssignment(projectId: string, reportId: string, change: {
  expectedVersion: number; developerId?: string; reason?: string
}, signal: AbortSignal): Promise<AssignmentResult> {
  const response = await mutate(`/api/projects/${projectId}/reports/${reportId}/assignment`, 'PATCH', JSON.stringify(change), undefined, signal)
  return mutationJson(response, (value): value is AssignmentResult => isAssignmentState(value) &&
    value.assignedDeveloperId === (change.developerId ?? null) && value.version > change.expectedVersion &&
    typeof value.updatedAt === 'string' && Number.isFinite(Date.parse(value.updatedAt)))
}
