import { afterEach, describe, expect, it, vi } from 'vitest'
import { AuthApiError } from '../auth/sessionApi'
import { createProject, fetchProjectDirectory, joinProject, normalizedProjectName, validProjectName } from './projectApi'

const projectId = '44b10495-a536-4c6e-a410-93bd2d9330fd'

function reply(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

afterEach(() => vi.unstubAllGlobals())

describe('project discovery and joining transport', () => {
  it('requests a bounded safe directory and validates paging metadata', async () => {
    const fetchMock = vi.fn().mockResolvedValue(reply(200, {
      items: [{ id: projectId, name: 'Example', membership: 'NONE' }],
      nextCursor: 'opaque-cursor', hasMore: true,
    }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(fetchProjectDirectory('prior-cursor')).resolves.toMatchObject({ hasMore: true })
    expect(fetchMock.mock.calls[0][0]).toBe('/api/projects?limit=50&cursor=prior-cursor')
    expect(fetchMock.mock.calls[0][1]).toMatchObject({ credentials: 'same-origin', cache: 'no-store' })
  })

  it('rejects malformed directory records and inconsistent cursors', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(reply(200, {
      items: [{ id: projectId, name: 'Example', membership: 'NONE' }], nextCursor: null, hasMore: true,
    })))
    await expect(fetchProjectDirectory()).rejects.toMatchObject({ status: 502, code: 'INVALID_RESPONSE' })
  })

  it('creates with a stable idempotency key through the CSRF-protected transport', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(reply(200, { token: 'csrf' }))
      .mockResolvedValueOnce(reply(201, { id: projectId, name: 'Example' }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(createProject('Example', 'stable-key')).resolves.toEqual({ id: projectId, name: 'Example' })
    expect(fetchMock.mock.calls[1][0]).toBe('/api/projects')
    expect(fetchMock.mock.calls[1][1]).toMatchObject({ method: 'POST', body: JSON.stringify({ name: 'Example' }) })
    expect(new Headers(fetchMock.mock.calls[1][1]?.headers).get('Idempotency-Key')).toBe('stable-key')
  })

  it('joins only canonical project identifiers and validates the active membership response', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(reply(200, { token: 'csrf' }))
      .mockResolvedValueOnce(reply(200, { project: { id: projectId, name: 'Example' }, membership: 'ACTIVE' }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(joinProject(projectId)).resolves.toEqual({ id: projectId, name: 'Example' })
    expect(fetchMock.mock.calls[1][0]).toBe(`/api/projects/${projectId}/join`)
    await expect(joinProject('not-a-uuid')).rejects.toBeInstanceOf(AuthApiError)
  })

  it('normalizes Unicode names and enforces bounded plain text', () => {
    expect(normalizedProjectName('  Cafe\u0301  ')).toBe('Café')
    expect(validProjectName('  Café  ')).toBe(true)
    expect(validProjectName('')).toBe(false)
    expect(validProjectName('bad\u0000name')).toBe(false)
    expect(validProjectName('x'.repeat(121))).toBe(false)
  })
})
