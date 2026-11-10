import { AuthApiError, csrfProtectedRequest } from '../auth/sessionApi'
import { isCanonicalUuid } from '../reports/reportApi'

export type ProjectMembershipState = 'ACTIVE' | 'DISABLED' | 'NONE'
export type DirectoryProject = {
  id: string
  name: string
  membership: ProjectMembershipState
}
export type ProjectDirectoryPage = {
  items: DirectoryProject[]
  nextCursor: string | null
  hasMore: boolean
}
export type ProjectSummary = { id: string; name: string }

function object(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

async function responseCode(response: Response): Promise<string | undefined> {
  try {
    const body: unknown = await response.clone().json()
    return object(body) && object(body.error) && typeof body.error.code === 'string'
      ? body.error.code
      : undefined
  } catch { return undefined }
}

async function checkedJson(response: Response): Promise<unknown> {
  if (!response.ok) throw new AuthApiError(response.status, await responseCode(response))
  try { return await response.json() } catch { throw new AuthApiError(502, 'INVALID_RESPONSE') }
}

function directoryProject(value: unknown): value is DirectoryProject {
  return object(value) && typeof value.id === 'string' && isCanonicalUuid(value.id) &&
    typeof value.name === 'string' && value.name.length > 0 &&
    ['ACTIVE', 'DISABLED', 'NONE'].includes(String(value.membership))
}

export async function fetchProjectDirectory(cursor?: string | null, signal?: AbortSignal): Promise<ProjectDirectoryPage> {
  const params = new URLSearchParams({ limit: '50' })
  if (cursor) params.set('cursor', cursor)
  let response: Response
  try {
    response = await fetch(`/api/projects?${params.toString()}`, {
      credentials: 'same-origin', cache: 'no-store', signal,
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new AuthApiError(0, 'NETWORK_ERROR')
  }
  const value = await checkedJson(response)
  if (!object(value) || !Array.isArray(value.items) || !value.items.every(directoryProject) ||
    (value.nextCursor !== null && typeof value.nextCursor !== 'string') ||
    typeof value.hasMore !== 'boolean' || value.hasMore !== Boolean(value.nextCursor)) {
    throw new AuthApiError(502, 'INVALID_RESPONSE')
  }
  const seen = new Set<string>()
  for (const item of value.items as DirectoryProject[]) {
    if (seen.has(item.id)) throw new AuthApiError(502, 'INVALID_RESPONSE')
    seen.add(item.id)
  }
  return value as ProjectDirectoryPage
}

export async function createProject(name: string, key: string = crypto.randomUUID()): Promise<ProjectSummary> {
  const response = await csrfProtectedRequest('/api/projects', JSON.stringify({ name }), { idempotencyKey: key })
  const value = await checkedJson(response)
  if (!object(value) || typeof value.id !== 'string' || !isCanonicalUuid(value.id) ||
    typeof value.name !== 'string' || value.name.length === 0) {
    throw new AuthApiError(502, 'INVALID_RESPONSE')
  }
  return { id: value.id, name: value.name }
}

export async function joinProject(projectId: string): Promise<ProjectSummary> {
  if (!isCanonicalUuid(projectId)) throw new AuthApiError(400, 'INVALID_REQUEST')
  const response = await csrfProtectedRequest(`/api/projects/${projectId}/join`)
  const value = await checkedJson(response)
  if (!object(value) || !object(value.project) || typeof value.project.id !== 'string' ||
    value.project.id !== projectId || typeof value.project.name !== 'string' ||
    value.membership !== 'ACTIVE') {
    throw new AuthApiError(502, 'INVALID_RESPONSE')
  }
  return { id: projectId, name: value.project.name }
}

export function normalizedProjectName(value: string): string {
  return value.normalize('NFC').trim()
}

export function validProjectName(value: string): boolean {
  const normalized = normalizedProjectName(value)
  const encoder = new TextEncoder()
  const hasControl = [...normalized].some(character => {
    const codePoint = character.codePointAt(0) ?? 0
    return (codePoint >= 0 && codePoint <= 8) || codePoint === 11 || codePoint === 12 ||
      (codePoint >= 14 && codePoint <= 31) || codePoint === 127
  })
  return normalized.length > 0 && [...normalized].length <= 120 && encoder.encode(normalized).length <= 480 &&
    !hasControl
}
