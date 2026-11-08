import { afterEach, describe, expect, it, vi } from 'vitest'
import { createReport, isCanonicalUuid, ReportApiError } from './reportApi'

const projectId = '44b10495-a536-4c6e-a410-93bd2d9330fd'
const reportId = '76989078-9c82-4ac5-9218-fc2b262f47e9'

function savedReport() {
  return {
    id: reportId,
    projectId,
    reporterUserId: 'f2e29c3e-2b62-4420-a631-b1300f148328',
    title: 'Saved report',
    description: 'Description',
    environment: 'Browser',
    reproductionSteps: 'Open the page',
    expectedBehavior: 'Works',
    actualBehavior: 'Fails',
    textLogs: '',
    status: 'OPEN',
    assignedDeveloperId: null,
    createdAt: '2026-11-08T00:00:00Z',
    updatedAt: '2026-11-08T00:00:00Z',
    version: 1,
  }
}

function reply(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

afterEach(() => vi.unstubAllGlobals())

describe('report submission transport', () => {
  it('accepts canonical UUID shape without deriving identity from it', () => {
    expect(isCanonicalUuid(projectId)).toBe(true)
    expect(isCanonicalUuid(projectId.toUpperCase())).toBe(false)
    expect(isCanonicalUuid(`${projectId}x`)).toBe(false)
  })

  it('retries only CSRF_INVALID once with the identical body and idempotency key', async () => {
    const body = JSON.stringify({ title: 'safe', textLogs: '' })
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(reply(200, { token: 'csrf-one' }))
      .mockResolvedValueOnce(reply(403, { error: { code: 'CSRF_INVALID' } }))
      .mockResolvedValueOnce(reply(200, { token: 'csrf-two' }))
      .mockResolvedValueOnce(reply(201, savedReport()))
    vi.stubGlobal('fetch', fetchMock)

    await expect(createReport(projectId, body, reportId)).resolves.toMatchObject({ id: reportId, status: 'OPEN' })

    const posts = fetchMock.mock.calls.filter(([url]) => url === `/api/projects/${projectId}/reports`)
    expect(posts).toHaveLength(2)
    for (const [, options] of posts) {
      expect(options).toMatchObject({ method: 'POST', body, credentials: 'same-origin', cache: 'no-store' })
      expect(new Headers(options?.headers).get('Idempotency-Key')).toBe(reportId)
    }
    expect(new Headers(posts[0][1]?.headers).get('X-CSRF-TOKEN')).toBe('csrf-one')
    expect(new Headers(posts[1][1]?.headers).get('X-CSRF-TOKEN')).toBe('csrf-two')
  })

  it('does not retry a business FORBIDDEN response', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(reply(200, { token: 'csrf' }))
      .mockResolvedValueOnce(reply(403, { error: { code: 'FORBIDDEN' } }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(createReport(projectId, '{}', reportId)).rejects.toMatchObject({ status: 403, code: 'FORBIDDEN' })
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('propagates CSRF lookup 401 without sending report text', async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(reply(401, { error: { code: 'UNAUTHENTICATED' } }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(createReport(projectId, '{"title":"private"}', reportId)).rejects.toBeInstanceOf(ReportApiError)
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(fetchMock.mock.calls[0][0]).toBe('/api/csrf')
  })

  it('does not send a pending report after project or session cancellation during CSRF lookup', async () => {
    let resolveCsrf!: (value: Response) => void
    const csrfResponse = new Promise<Response>(resolve => { resolveCsrf = resolve })
    const fetchMock = vi.fn().mockReturnValueOnce(csrfResponse)
    vi.stubGlobal('fetch', fetchMock)
    const controller = new AbortController()
    const pending = createReport(projectId, '{"title":"private"}', reportId, controller.signal)
    controller.abort()
    resolveCsrf(reply(200, { token: 'csrf' }))

    await expect(pending).rejects.toMatchObject({ name: 'AbortError' })
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(fetchMock.mock.calls[0][0]).toBe('/api/csrf')
  })
})
