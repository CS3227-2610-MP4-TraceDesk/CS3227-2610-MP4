import { afterEach, describe, expect, it, vi } from 'vitest'
import { getSession, login, logout } from './sessionApi'

function reply(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

afterEach(() => vi.unstubAllGlobals())

describe('session API client', () => {
  it('loads a server session without caching auth data', async () => {
    const fetchMock = vi.fn().mockResolvedValue(reply(200, { userId: 'synthetic-user', role: 'REPORTER' }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(getSession()).resolves.toEqual({ userId: 'synthetic-user', role: 'REPORTER' })
    expect(fetchMock).toHaveBeenCalledWith('/api/session', expect.objectContaining({
      credentials: 'same-origin',
      cache: 'no-store',
    }))
  })

  it('sends only login and password with the JSON CSRF token', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(reply(200, { token: 'token-one' }))
      .mockResolvedValueOnce(reply(200, { userId: 'synthetic-user' }))
    vi.stubGlobal('fetch', fetchMock)

    await login('person@example.test', 'synthetic-password')

    expect(fetchMock.mock.calls[0][0]).toBe('/api/csrf')
    expect(fetchMock.mock.calls[1][0]).toBe('/api/session/login')
    expect(fetchMock.mock.calls[1][1]).toMatchObject({
      method: 'POST',
      credentials: 'same-origin',
      body: JSON.stringify({ login: 'person@example.test', password: 'synthetic-password' }),
    })
    const headers = new Headers(fetchMock.mock.calls[1][1]?.headers)
    expect(headers.get('Content-Type')).toBe('application/json')
    expect(headers.get('X-CSRF-TOKEN')).toBe('token-one')
  })

  it('refreshes CSRF once after a rejected token and retries the unchanged request', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(reply(200, { token: 'stale-token' }))
      .mockResolvedValueOnce(reply(403, { error: { code: 'CSRF_INVALID' } }))
      .mockResolvedValueOnce(reply(200, { token: 'fresh-token' }))
      .mockResolvedValueOnce(reply(200, { userId: 'synthetic-user' }))
    vi.stubGlobal('fetch', fetchMock)

    await login('person@example.test', 'synthetic-password')

    expect(fetchMock).toHaveBeenCalledTimes(4)
    expect(fetchMock.mock.calls[1][1]).toMatchObject({
      body: JSON.stringify({ login: 'person@example.test', password: 'synthetic-password' }),
    })
    expect(new Headers(fetchMock.mock.calls[3][1]?.headers).get('X-CSRF-TOKEN')).toBe('fresh-token')
  })

  it('fetches a new CSRF token for logout', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(reply(200, { token: 'logout-token' }))
      .mockResolvedValueOnce(reply(204))
    vi.stubGlobal('fetch', fetchMock)

    await logout()

    expect(fetchMock.mock.calls[1][0]).toBe('/api/session/logout')
    expect(fetchMock.mock.calls[1][1]).toMatchObject({
      method: 'POST',
      credentials: 'same-origin',
    })
    expect(new Headers(fetchMock.mock.calls[1][1]?.headers).get('X-CSRF-TOKEN')).toBe('logout-token')
    expect(fetchMock.mock.calls[1][1]).not.toHaveProperty('body')
  })

  it('does not treat an unconfirmed 2xx logout response as success', async () => {
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(reply(200, { token: 'logout-token' }))
      .mockResolvedValueOnce(reply(202)))

    await expect(logout()).rejects.toMatchObject({ status: 202 })
  })

  it('preserves status codes for safe UI handling without exposing response text', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(reply(401, {
      error: { code: 'INVALID_CREDENTIALS', message: 'Invalid credentials' },
    })))

    await expect(getSession()).rejects.toMatchObject({ status: 401 })
  })

  it('does not retry repeatedly after a second CSRF rejection', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(reply(200, { token: 'first-token' }))
      .mockResolvedValueOnce(reply(403, { error: { code: 'CSRF_INVALID' } }))
      .mockResolvedValueOnce(reply(200, { token: 'second-token' }))
      .mockResolvedValueOnce(reply(403, { error: { code: 'CSRF_INVALID' } }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(login('person@example.test', 'synthetic-password')).rejects.toMatchObject({
      status: 403,
      code: 'CSRF_INVALID',
    })
    expect(fetchMock).toHaveBeenCalledTimes(4)
  })
})
