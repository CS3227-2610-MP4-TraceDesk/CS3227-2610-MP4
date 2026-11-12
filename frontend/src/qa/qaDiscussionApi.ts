import { AuthApiError, getCsrfToken } from '../auth/sessionApi'

export type QaComment = {
  id: string
  authorUserId: string
  body: string
  createdAt: string
}

export type QaCommentPage = {
  items: QaComment[]
  nextCursor: string | null
}

export class QaDiscussionApiError extends Error {
  constructor(readonly status: number, readonly code?: string) {
    super('QA discussion request failed')
    this.name = 'QaDiscussionApiError'
  }
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function isComment(value: unknown): value is QaComment {
  return isObject(value) && typeof value.id === 'string' && value.id.length > 0 &&
    typeof value.authorUserId === 'string' && value.authorUserId.length > 0 &&
    typeof value.body === 'string' && typeof value.createdAt === 'string' &&
    Number.isFinite(Date.parse(value.createdAt))
}

function isCommentPage(value: unknown): value is QaCommentPage {
  return isObject(value) && Array.isArray(value.items) && value.items.length <= 100 &&
    value.items.every(isComment) &&
    (value.nextCursor === null || (typeof value.nextCursor === 'string' && value.nextCursor.length > 0))
}

async function readErrorCode(response: Response): Promise<string | undefined> {
  try {
    const value: unknown = await response.clone().json()
    return isObject(value) && isObject(value.error) && typeof value.error.code === 'string'
      ? value.error.code
      : undefined
  } catch {
    return undefined
  }
}

async function readJson<T>(response: Response, validate: (value: unknown) => value is T): Promise<T> {
  if (!response.ok) throw new QaDiscussionApiError(response.status, await readErrorCode(response))

  const raw = await response.text()
  if (new TextEncoder().encode(raw).length > 256 * 1024) throw new QaDiscussionApiError(502, 'INVALID_RESPONSE')

  let value: unknown
  try {
    value = JSON.parse(raw)
  } catch {
    throw new QaDiscussionApiError(502, 'INVALID_RESPONSE')
  }
  if (!validate(value)) throw new QaDiscussionApiError(502, 'INVALID_RESPONSE')
  return value
}

function commentsPath(projectId: string, reportId: string): string {
  return `/api/projects/${encodeURIComponent(projectId)}/reports/${encodeURIComponent(reportId)}/qa-comments`
}

export async function fetchQaComments(
  projectId: string,
  reportId: string,
  signal: AbortSignal,
  before?: string,
): Promise<QaCommentPage> {
  const params = new URLSearchParams()
  if (before) params.set('before', before)

  try {
    const response = await fetch(`${commentsPath(projectId, reportId)}${params.size ? `?${params}` : ''}`, {
      credentials: 'same-origin',
      cache: 'no-store',
      signal,
    })
    return await readJson(response, isCommentPage)
  } catch (error) {
    if (error instanceof QaDiscussionApiError || (error instanceof DOMException && error.name === 'AbortError')) throw error
    throw new QaDiscussionApiError(0, 'NETWORK_ERROR')
  }
}

export async function postQaComment(projectId: string, reportId: string, body: string, signal: AbortSignal): Promise<QaComment> {
  try {
    const token = await getCsrfToken(signal)
    const response = await fetch(commentsPath(projectId, reportId), {
      method: 'POST',
      credentials: 'same-origin',
      cache: 'no-store',
      signal,
      headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': token },
      body: JSON.stringify({ body }),
    })
    return await readJson(response, isComment)
  } catch (error) {
    if (error instanceof QaDiscussionApiError || (error instanceof DOMException && error.name === 'AbortError')) throw error
    if (error instanceof AuthApiError) throw new QaDiscussionApiError(error.status, error.code)
    throw new QaDiscussionApiError(0, 'NETWORK_ERROR')
  }
}
