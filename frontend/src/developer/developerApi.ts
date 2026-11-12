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
  rejection?: null | { reason: string; reviewerUserId: string; createdAt: string }
}
export type ResolutionPage = { items: ProposedResolution[]; nextCursor: string | null }
export type ResolutionProposal = { report: ReportDetail; resolution: ProposedResolution }
export type DeveloperReportScope = 'assigned-to-me' | 'all'
export const investigationReportFields = ['title', 'description', 'environment', 'reproductionSteps', 'expectedBehavior', 'actualBehavior', 'textLogs'] as const
export type InvestigationReportField = typeof investigationReportFields[number]
export type InvestigationSelection = {
  expectedVersion: number
  reportFields: InvestigationReportField[]
  notes: { id: string; expectedVersion: number }[]
}
export type InvestigationSuggestion = {
  capability: 'INVESTIGATION'
  promptVersion: 'investigation-suggestion-v1'
  reportId: string
  reportVersion: number
  reportFields: InvestigationReportField[]
  sourceNotes: { ref: string; id: string; version: number }[]
  hypotheses: { statement: string; rationale: string; sources: string[] }[]
  nextSteps: string[]
}

export class DeveloperApiError extends Error {
  constructor(readonly status: number, readonly code?: string, readonly currentVersion?: number) {
    super('Developer request failed')
    this.name = 'DeveloperApiError'
  }
}

function object(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function exactKeys(value: Record<string, unknown>, expected: readonly string[]) {
  return Object.keys(value).length === expected.length && expected.every(key => Object.hasOwn(value, key))
}

function safeAiText(value: unknown, maximumCodePoints: number, maximumBytes: number): value is string {
  if (typeof value !== 'string' || !value.trim() || Array.from(value).length > maximumCodePoints ||
    new TextEncoder().encode(value).length > maximumBytes) return false
  for (const character of value) {
    const code = character.codePointAt(0) ?? 0
    if ((code < 32 && code !== 9 && code !== 10 && code !== 13) || (code >= 127 && code <= 159) ||
      (code >= 0xd800 && code <= 0xdfff)) return false
  }
  return true
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
  if (!object(value)) return false
  const rejectionValid = !('rejection' in value) || value.rejection === null ||
    (object(value.rejection) && typeof value.rejection.reason === 'string' && value.rejection.reason.trim().length > 0 &&
      Array.from(value.rejection.reason).length <= 2_000 && typeof value.rejection.reviewerUserId === 'string' &&
      isCanonicalUuid(value.rejection.reviewerUserId) && typeof value.rejection.createdAt === 'string' &&
      Number.isFinite(Date.parse(value.rejection.createdAt)))
  return isCanonicalUuid(String(value.id)) && value.reportId === reportId &&
    isCanonicalUuid(String(value.authorUserId)) && Number.isInteger(value.revision) && Number(value.revision) > 0 &&
    isBoundedText(value.rootCause, 4_000) && isBoundedText(value.changeSummary, 4_000) &&
    isBoundedText(value.verificationSteps, 4_000) && isBoundedText(value.fixReference, 200) &&
    typeof value.createdAt === 'string' && Number.isFinite(Date.parse(value.createdAt)) && rejectionValid
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

function isInvestigationSuggestion(value: unknown, reportId: string, selection: InvestigationSelection): value is InvestigationSuggestion {
  if (!object(value) || !exactKeys(value, ['capability', 'promptVersion', 'reportId', 'reportVersion', 'reportFields', 'sourceNotes', 'hypotheses', 'nextSteps']) ||
    value.capability !== 'INVESTIGATION' || value.promptVersion !== 'investigation-suggestion-v1' ||
    value.reportId !== reportId || value.reportVersion !== selection.expectedVersion ||
    !Array.isArray(value.reportFields) || value.reportFields.length !== selection.reportFields.length ||
    !value.reportFields.every((field, index) => field === selection.reportFields[index]) ||
    !Array.isArray(value.sourceNotes) || value.sourceNotes.length !== selection.notes.length ||
    !Array.isArray(value.hypotheses) || value.hypotheses.length > 3 ||
    !Array.isArray(value.nextSteps) || value.nextSteps.length > 4) return false

  const sourceLabels = new Set<string>(selection.reportFields)
  for (let index = 0; index < value.sourceNotes.length; index += 1) {
    const source = value.sourceNotes[index]
    const expected = selection.notes[index]
    if (!object(source) || !exactKeys(source, ['ref', 'id', 'version']) || source.ref !== `note${index + 1}` ||
      source.id !== expected.id || source.version !== expected.expectedVersion) return false
    sourceLabels.add(source.ref)
  }
  for (const hypothesis of value.hypotheses) {
    if (!object(hypothesis) || !exactKeys(hypothesis, ['statement', 'rationale', 'sources']) ||
      !safeAiText(hypothesis.statement, 300, 1_200) || !safeAiText(hypothesis.rationale, 500, 2_000) ||
      !Array.isArray(hypothesis.sources) || hypothesis.sources.length < 1 || hypothesis.sources.length > 4 ||
      new Set(hypothesis.sources).size !== hypothesis.sources.length ||
      !hypothesis.sources.every(source => typeof source === 'string' && sourceLabels.has(source))) return false
  }
  return value.nextSteps.every(step => safeAiText(step, 240, 960))
}

/** A generation has no idempotency key and is never automatically replayed. */
export async function requestInvestigationSuggestions(projectId: string, reportId: string, selection: InvestigationSelection, signal?: AbortSignal): Promise<InvestigationSuggestion> {
  if (!isCanonicalUuid(projectId) || !isCanonicalUuid(reportId) || !Number.isSafeInteger(selection.expectedVersion) || selection.expectedVersion < 1 ||
    selection.reportFields.length < 1 || selection.reportFields.length > investigationReportFields.length ||
    new Set(selection.reportFields).size !== selection.reportFields.length ||
    !selection.reportFields.every(field => investigationReportFields.includes(field)) || selection.notes.length > 5 ||
    new Set(selection.notes.map(note => note.id)).size !== selection.notes.length ||
    !selection.notes.every(note => isCanonicalUuid(note.id) && Number.isSafeInteger(note.expectedVersion) && note.expectedVersion > 0)) {
    throw new DeveloperApiError(400, 'INVALID_REQUEST')
  }
  try {
    const token = await getCsrfToken(signal)
    if (signal?.aborted) throw new DOMException('Request aborted', 'AbortError')
    const response = await fetch(`/api/projects/${projectId}/reports/${reportId}/investigation/suggestions`, {
      method: 'POST', credentials: 'same-origin', cache: 'no-store', signal,
      headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': token },
      body: JSON.stringify(selection),
    })
    if (response.status !== 200) throw await failure(response)
    let value: unknown
    try {
      const raw = await response.text()
      if (new TextEncoder().encode(raw).length > 24 * 1024) throw new Error('Invalid response')
      value = JSON.parse(raw)
    } catch { throw new DeveloperApiError(502, 'INVALID_RESPONSE') }
    if (!isInvestigationSuggestion(value, reportId, selection)) throw new DeveloperApiError(502, 'INVALID_RESPONSE')
    return value
  } catch (error) {
    if (signal?.aborted) throw new DOMException('Request aborted', 'AbortError')
    if (error instanceof DeveloperApiError) throw error
    if (error instanceof AuthApiError) throw new DeveloperApiError(error.status, error.code)
    throw new DeveloperApiError(0, 'NETWORK_ERROR')
  }
}

export async function updateNote(projectId: string, reportId: string, noteId: string, body: string, expectedVersion: number, signal?: AbortSignal): Promise<InvestigationNote> {
  return json(await mutate(`/api/projects/${projectId}/reports/${reportId}/notes/${noteId}`, 'PATCH', JSON.stringify({ body, expectedVersion }), undefined, signal),
    (value): value is InvestigationNote => isNote(value, projectId) && value.reportId === reportId && value.id === noteId)
}

export async function fetchResolutions(projectId: string, reportId: string, signal?: AbortSignal, cursor?: string): Promise<ResolutionPage> {
  const query = new URLSearchParams({ limit: '100' })
  if (cursor) query.set('cursor', cursor)
  return json(await read(`/api/projects/${projectId}/reports/${reportId}/resolutions?${query}`, signal), (value): value is ResolutionPage =>
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
