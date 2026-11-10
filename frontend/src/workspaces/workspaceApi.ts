import { AuthApiError } from '../auth/sessionApi'
import { isCanonicalUuid } from '../reports/reportApi'

export type WorkspaceRole = 'REPORTER' | 'DEVELOPER' | 'QA_REVIEWER'
export type WorkspaceMembership = { project: { id: string; name: string }; role: WorkspaceRole }
export type WorkspacePage = { items: WorkspaceMembership[]; hasMore: boolean }

const roles = new Set<WorkspaceRole>(['REPORTER', 'DEVELOPER', 'QA_REVIEWER'])

function object(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function membership(value: unknown): value is WorkspaceMembership {
  return object(value) && object(value.project) && typeof value.project.id === 'string' &&
    isCanonicalUuid(value.project.id) && typeof value.project.name === 'string' && value.project.name.length > 0 &&
    typeof value.role === 'string' && roles.has(value.role as WorkspaceRole)
}

async function code(response: Response): Promise<string | undefined> {
  try {
    const value: unknown = await response.clone().json()
    return object(value) && object(value.error) && typeof value.error.code === 'string' ? value.error.code : undefined
  } catch { return undefined }
}

export async function fetchWorkspaces(signal?: AbortSignal): Promise<WorkspacePage> {
  let response: Response
  try {
    response = await fetch('/api/workspaces', { credentials: 'same-origin', cache: 'no-store', signal })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new AuthApiError(0, 'NETWORK_ERROR')
  }
  if (!response.ok) throw new AuthApiError(response.status, await code(response))
  let value: unknown
  try { value = await response.json() } catch { throw new AuthApiError(502, 'INVALID_RESPONSE') }
  if (!object(value) || !Array.isArray(value.items) || !value.items.every(membership) || typeof value.hasMore !== 'boolean') {
    throw new AuthApiError(502, 'INVALID_RESPONSE')
  }
  const seen = new Set<string>()
  for (const item of value.items) {
    if (seen.has(item.project.id)) throw new AuthApiError(502, 'INVALID_RESPONSE')
    seen.add(item.project.id)
  }
  return value as WorkspacePage
}
