import type { Page } from '@playwright/test'

export type AuthRequest = {
  method: string
  path: string
  headers: Record<string, string>
  body: string | undefined
}

export async function mockAuthApi(page: Page, options: {
  loginStatus?: number
  logoutStatus?: number
  sessionStatuses?: number[]
} = {}) {
  const requests: AuthRequest[] = []
  let sessionCall = 0
  await page.route('**/api/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    const headers = await request.allHeaders()
    requests.push({ method: request.method(), path, headers, body: request.postData() ?? undefined })

    if (path === '/api/session' && request.method() === 'GET') {
      const configured = options.sessionStatuses?.[sessionCall]
      sessionCall += 1
      if (configured && configured !== 200 && configured !== 401) {
        await route.fulfill({ status: configured, contentType: 'application/json', body: '{"error":{"code":"SERVICE_UNAVAILABLE","message":"Service unavailable"}}' })
      } else if (headers.cookie?.includes('TDSESSION=synthetic-session')) {
        await route.fulfill({ status: 200, contentType: 'application/json', body: '{"userId":"synthetic-user"}' })
      } else {
        await route.fulfill({ status: 401, contentType: 'application/json', body: '{"error":{"code":"UNAUTHENTICATED","message":"Authentication required"}}' })
      }
      return
    }

    if (path === '/api/csrf' && request.method() === 'GET') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '{"token":"synthetic-csrf-token"}' })
      return
    }

    if (path === '/api/session/login' && request.method() === 'POST') {
      if ((options.loginStatus ?? 200) === 200) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          headers: { 'Set-Cookie': 'TDSESSION=synthetic-session; Path=/; HttpOnly; Secure; SameSite=Lax' },
          body: '{"userId":"synthetic-user"}',
        })
      } else {
        const status = options.loginStatus ?? 401
        const code = status === 401 ? 'INVALID_CREDENTIALS' : status === 403 ? 'CSRF_INVALID' : 'SERVICE_UNAVAILABLE'
        await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify({ error: { code, message: 'Service unavailable' } }) })
      }
      return
    }

    if (path === '/api/session/logout' && request.method() === 'POST') {
      const status = options.logoutStatus ?? 204
      await route.fulfill({
        status,
        contentType: 'application/json',
        headers: status === 204 || status === 401
          ? { 'Set-Cookie': 'TDSESSION=; Path=/; Max-Age=0; HttpOnly; Secure; SameSite=Lax' }
          : undefined,
        body: status === 204 ? '' : JSON.stringify({ error: { code: status === 401 ? 'UNAUTHENTICATED' : 'SERVICE_UNAVAILABLE', message: 'Service unavailable' } }),
      })
      return
    }

    await route.fulfill({ status: 404, contentType: 'application/json', body: '{"error":{"code":"NOT_FOUND","message":"Not found"}}' })
  })
  return requests
}
