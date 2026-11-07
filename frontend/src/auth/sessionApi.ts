export class AuthApiError extends Error {
  constructor(
    readonly status: number,
    readonly code?: string,
  ) {
    super('Authentication request failed')
    this.name = 'AuthApiError'
  }
}

type ApiFailure = { error?: { code?: unknown } }

async function responseCode(response: Response): Promise<string | undefined> {
  try {
    const body = await response.json() as ApiFailure
    return typeof body?.error?.code === 'string' ? body.error.code : undefined
  } catch {
    return undefined
  }
}

async function fail(response: Response): Promise<never> {
  throw new AuthApiError(response.status, await responseCode(response))
}

export async function getSession(signal?: AbortSignal): Promise<{ userId: string }> {
  const response = await fetch('/api/session', {
    credentials: 'same-origin',
    cache: 'no-store',
    signal,
  })
  if (!response.ok) return fail(response)

  const session: unknown = await response.json()
  if (typeof session !== 'object' || session === null ||
    typeof (session as { userId?: unknown }).userId !== 'string') {
    throw new AuthApiError(502, 'INVALID_RESPONSE')
  }
  return session as { userId: string }
}

async function getCsrfToken(): Promise<string> {
  const response = await fetch('/api/csrf', {
    credentials: 'same-origin',
    cache: 'no-store',
  })
  if (!response.ok) return fail(response)

  const body: unknown = await response.json()
  if (typeof body !== 'object' || body === null ||
    typeof (body as { token?: unknown }).token !== 'string' ||
    (body as { token: string }).token.length === 0) {
    throw new AuthApiError(502, 'INVALID_RESPONSE')
  }
  return (body as { token: string }).token
}

async function csrfProtectedRequest(path: string, body?: string): Promise<Response> {
  for (let attempt = 0; attempt < 2; attempt += 1) {
    const token = await getCsrfToken()
    const headers = new Headers({ 'X-CSRF-TOKEN': token })
    if (body !== undefined) headers.set('Content-Type', 'application/json')
    const response = await fetch(path, {
      method: 'POST',
      credentials: 'same-origin',
      cache: 'no-store',
      headers,
      ...(body === undefined ? {} : { body }),
    })
    if (response.status === 403 && attempt === 0) continue
    return response
  }
  throw new AuthApiError(403, 'CSRF_INVALID')
}

export async function login(login: string, password: string): Promise<void> {
  const response = await csrfProtectedRequest('/api/session/login', JSON.stringify({ login, password }))
  if (!response.ok) return fail(response)
}

export async function logout(): Promise<void> {
  const response = await csrfProtectedRequest('/api/session/logout')
  if (response.status !== 204) return fail(response)
}
