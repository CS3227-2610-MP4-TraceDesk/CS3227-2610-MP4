import { AuthApiError, getCsrfToken } from '../auth/sessionApi'
import { isCanonicalUuid, ReportApiError } from '../reports/reportApi'
import type { TestTemplate } from '../preview/qa/qaTemplateFixtures'

export type QaTemplateRecord = TestTemplate & { version: number }
type QaTemplateCase = { name: string; steps: string; expectedResult: string }
type QaTemplateResponse = { id: string; version: number; name: string; whenToUse: string; preconditions: string; testCases: QaTemplateCase[] }
type QaTemplatePayload = { name: string; whenToUse: string; preconditions: string; testCases: QaTemplateCase[] }

function object(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function text(value: unknown, maximum: number, nonblank = false): value is string {
  return typeof value === 'string' && Array.from(value).length <= maximum && (!nonblank || value.trim().length > 0)
}

function isRecord(value: unknown): value is QaTemplateResponse {
  return object(value) && isCanonicalUuid(String(value.id)) && Number.isInteger(value.version) && Number(value.version) > 0 &&
    text(value.name, 120, true) && text(value.whenToUse, 1_000) && text(value.preconditions, 1_000) &&
    Array.isArray(value.testCases) && value.testCases.length >= 1 && value.testCases.length <= 30 &&
    value.testCases.every(testCase => object(testCase) && text(testCase.name, 300, true) &&
      text(testCase.steps, 1_000) && text(testCase.expectedResult, 1_000)) &&
    new Set(value.testCases.map(testCase => String((testCase as Record<string, unknown>).name).trim().toLowerCase())).size === value.testCases.length
}

function withEditorIds(record: QaTemplateResponse): QaTemplateRecord {
  const cases = record.testCases ?? []
  return {
    ...record,
    testCases: cases.map((testCase, index) => ({ ...testCase, id: `${record.id}:${index}` })),
    checks: cases.map(testCase => testCase.name),
  }
}

function payload(template: TestTemplate): QaTemplatePayload {
  const cases = template.testCases?.length
    ? template.testCases
    : template.checks.map(name => ({ id: '', name, steps: '', expectedResult: '' }))
  return {
    name: template.name,
    whenToUse: template.whenToUse ?? '',
    preconditions: template.preconditions ?? '',
    testCases: cases.map(({ name, steps, expectedResult }) => ({ name, steps, expectedResult })),
  }
}

async function readCode(response: Response): Promise<string | undefined> {
  try {
    const body: unknown = await response.clone().json()
    const error = object(body) && object(body.error) ? body.error : undefined
    return typeof error?.code === 'string' ? error.code : undefined
  } catch { return undefined }
}

async function request(path: string, method = 'GET', body?: string): Promise<Response> {
  let headers: Record<string, string> = {}
  if (method !== 'GET') {
    try { headers = { 'X-CSRF-TOKEN': await getCsrfToken() } }
    catch (error) {
      if (error instanceof AuthApiError) throw new ReportApiError(error.status, error.code)
      throw new ReportApiError(0, 'NETWORK_ERROR')
    }
    if (body !== undefined) headers['Content-Type'] = 'application/json'
  }
  let response: Response
  try {
    response = await fetch(path, { method, credentials: 'same-origin', cache: 'no-store', headers, ...(body === undefined ? {} : { body }) })
  } catch { throw new ReportApiError(0, 'NETWORK_ERROR') }
  if (method !== 'GET' && response.status === 403 && await readCode(response) === 'CSRF_INVALID') {
    let token: string
    try { token = await getCsrfToken() }
    catch (error) {
      if (error instanceof AuthApiError) throw new ReportApiError(error.status, error.code)
      throw new ReportApiError(0, 'NETWORK_ERROR')
    }
    try {
      response = await fetch(path, { method, credentials: 'same-origin', cache: 'no-store', headers: {
        'X-CSRF-TOKEN': token, ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      }, ...(body === undefined ? {} : { body }) })
    } catch { throw new ReportApiError(0, 'NETWORK_ERROR') }
  }
  return response
}

async function readJson<T>(response: Response, validate: (value: unknown) => value is T): Promise<T> {
  if (!response.ok) throw new ReportApiError(response.status, await readCode(response))
  let value: unknown
  try { value = await response.json() } catch { throw new ReportApiError(502, 'INVALID_RESPONSE') }
  if (!validate(value)) throw new ReportApiError(502, 'INVALID_RESPONSE')
  return value
}

function validProject(projectId: string): void {
  if (!isCanonicalUuid(projectId)) throw new ReportApiError(400, 'INVALID_REQUEST')
}

export async function fetchQaTemplates(projectId: string): Promise<QaTemplateRecord[]> {
  validProject(projectId)
  const records = await readJson(await request(`/api/projects/${projectId}/qa-templates`),
    (value): value is QaTemplateResponse[] => Array.isArray(value) && value.every(isRecord))
  return records.map(withEditorIds)
}

export async function createQaTemplate(projectId: string, template: TestTemplate): Promise<QaTemplateRecord> {
  validProject(projectId)
  const record = await readJson(await request(`/api/projects/${projectId}/qa-templates`, 'POST', JSON.stringify(payload(template))), isRecord)
  return withEditorIds(record)
}

export async function updateQaTemplate(projectId: string, templateId: string, expectedVersion: number, template: TestTemplate): Promise<QaTemplateRecord> {
  validProject(projectId)
  if (!isCanonicalUuid(templateId) || !Number.isInteger(expectedVersion) || expectedVersion < 1) {
    throw new ReportApiError(400, 'INVALID_REQUEST')
  }
  const record = await readJson(await request(`/api/projects/${projectId}/qa-templates/${templateId}`, 'PATCH', JSON.stringify({ ...payload(template), expectedVersion })), isRecord)
  if (record.id !== templateId) throw new ReportApiError(502, 'INVALID_RESPONSE')
  return withEditorIds(record)
}

export async function deleteQaTemplate(projectId: string, templateId: string, expectedVersion: number): Promise<void> {
  validProject(projectId)
  if (!isCanonicalUuid(templateId) || !Number.isInteger(expectedVersion) || expectedVersion < 1) {
    throw new ReportApiError(400, 'INVALID_REQUEST')
  }
  const response = await request(`/api/projects/${projectId}/qa-templates/${templateId}?expectedVersion=${expectedVersion}`, 'DELETE')
  if (response.status !== 204) throw new ReportApiError(response.status, await readCode(response))
}
